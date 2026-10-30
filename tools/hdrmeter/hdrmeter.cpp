// hdrmeter: reads back what Windows actually sends to the HDR monitor (Desktop Duplication, FP16 scRGB,
// 1.0 = 80 nits) for one window's client area and prints brightness statistics.
//
//   hdrmeter once  <window-title-part> [paperWhiteNits]
//   hdrmeter watch <status-file> <window-title-part> <out-file> [paperWhiteNits]
//       watch: every time the status file's first line changes, capture and append a line to out-file;
//              stops when the status starts with "finished".
// Works for windowed / composed windows (an independent-flip full-screen window is invisible to duplication).
// Build: g++ -O2 -std=c++17 -o hdrmeter.exe hdrmeter.cpp -ld3d11 -ldxgi -luser32 -ldwmapi
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#define _WIN32_WINNT 0x0A00
#include <windows.h>
#include <d3d11.h>
#include <dxgi1_6.h>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <map>
#include <string>
#include <vector>
#include <algorithm>

static float half2f(uint16_t h) {
  uint32_t s = (h >> 15) & 1, e = (h >> 10) & 31, f = h & 1023; float v;
  if (e == 0) v = std::ldexp((float)f, -24); else if (e == 31) v = INFINITY; else v = std::ldexp((float)(f | 1024), (int)e - 25);
  return s ? -v : v;
}

struct Finder { std::string part; HWND found = nullptr; };
static BOOL CALLBACK enumProc(HWND h, LPARAM lp) {
  auto* f = (Finder*)lp; if (!IsWindowVisible(h)) return TRUE;
  char t[512]; GetWindowTextA(h, t, sizeof t);
  if (!*t || strstr(t, "hdrmeter")) return TRUE;
  size_t a = 0; // part may list alternatives: "Minecraft|New Horizons"
  while (a <= f->part.size()) {
    size_t b = f->part.find('|', a); if (b == std::string::npos) b = f->part.size();
    std::string p = f->part.substr(a, b - a);
    if (!p.empty() && strstr(t, p.c_str())) { f->found = h; return FALSE; }
    a = b + 1;
  }
  return TRUE;
}

struct Meter {
  IDXGIOutputDuplication* dup = nullptr; ID3D11Device* dev = nullptr; ID3D11DeviceContext* ctx = nullptr;
  RECT desk{}; float sdrWhite = 80;
  bool init() {
    IDXGIFactory6* f; if (FAILED(CreateDXGIFactory2(0, __uuidof(IDXGIFactory6), (void**)&f))) return false;
    IDXGIAdapter1* a;
    for (UINT i = 0; f->EnumAdapters1(i, &a) != DXGI_ERROR_NOT_FOUND; i++) {
      IDXGIOutput* o;
      for (UINT j = 0; a->EnumOutputs(j, &o) != DXGI_ERROR_NOT_FOUND; j++) {
        IDXGIOutput6* o6; o->QueryInterface(__uuidof(IDXGIOutput6), (void**)&o6);
        DXGI_OUTPUT_DESC1 d; o6->GetDesc1(&d);
        if (d.ColorSpace == DXGI_COLOR_SPACE_RGB_FULL_G2084_NONE_P2020 && !dup) {
          desk = d.DesktopCoordinates;
          D3D_FEATURE_LEVEL fl = D3D_FEATURE_LEVEL_11_0;
          D3D11CreateDevice(a, D3D_DRIVER_TYPE_UNKNOWN, nullptr, 0, &fl, 1, D3D11_SDK_VERSION, &dev, nullptr, &ctx);
          IDXGIOutput5* o5; o6->QueryInterface(__uuidof(IDXGIOutput5), (void**)&o5);
          DXGI_FORMAT fmts[] = { DXGI_FORMAT_R16G16B16A16_FLOAT };
          if (FAILED(o5->DuplicateOutput1(dev, 0, 1, fmts, &dup))) dup = nullptr;
        }
      }
    }
    return dup != nullptr;
  }
  // Returns a text line with the statistics of the window's client area.
  std::string capture(HWND w, float paper) {
    RECT rc; GetClientRect(w, &rc); POINT tl{0, 0}; ClientToScreen(w, &tl);
    int x0 = tl.x - desk.left, y0 = tl.y - desk.top, cw = rc.right, ch = rc.bottom;
    for (int attempt = 0; attempt < 20; attempt++) {
      DXGI_OUTDUPL_FRAME_INFO fi; IDXGIResource* res = nullptr;
      HRESULT hr = dup->AcquireNextFrame(200, &fi, &res);
      if (hr == DXGI_ERROR_WAIT_TIMEOUT) continue;
      if (FAILED(hr)) return "capture failed";
      if (fi.LastPresentTime.QuadPart == 0) { res->Release(); dup->ReleaseFrame(); continue; } // pointer-only update
      ID3D11Texture2D* tex; res->QueryInterface(__uuidof(ID3D11Texture2D), (void**)&tex);
      D3D11_TEXTURE2D_DESC td; tex->GetDesc(&td);
      D3D11_TEXTURE2D_DESC sd = td; sd.Usage = D3D11_USAGE_STAGING; sd.BindFlags = 0; sd.CPUAccessFlags = D3D11_CPU_ACCESS_READ; sd.MiscFlags = 0;
      ID3D11Texture2D* st; dev->CreateTexture2D(&sd, nullptr, &st); ctx->CopyResource(st, tex);
      D3D11_MAPPED_SUBRESOURCE m; ctx->Map(st, 0, D3D11_MAP_READ, 0, &m);
      int xa = std::max(0, x0), ya = std::max(0, y0), xb = std::min((int)td.Width, x0 + cw), yb = std::min((int)td.Height, y0 + ch);
      double sumY = 0, sumMax = 0, maxNits = 0; long n = 0, abovePaper = 0, above300 = 0; std::vector<float> all;
      std::map<int, long> bright; // brightest-channel nits rounded to 5, for pixels >= 120 nits
      for (int y = ya; y < yb; y++) {
        const uint16_t* row = (const uint16_t*)((const uint8_t*)m.pData + y * m.RowPitch);
        for (int x = xa; x < xb; x++) {
          const uint16_t* p = row + x * 4;
          float r = half2f(p[0]) * 80, g = half2f(p[1]) * 80, b = half2f(p[2]) * 80;
          float mx = std::max(r, std::max(g, b)), Y = 0.2126f * r + 0.7152f * g + 0.0722f * b;
          sumY += Y; sumMax += mx; n++; if (mx > maxNits) maxNits = mx;
          if (mx > paper * 1.02f) abovePaper++;
          if (mx > 300) above300++;
          if (mx >= 120) bright[(int)std::lround(mx / 5) * 5]++;
          if ((x & 3) == 0 && (y & 3) == 0) all.push_back(mx);
        }
      }
      ctx->Unmap(st, 0); st->Release(); tex->Release(); res->Release(); dup->ReleaseFrame();
      if (n == 0) return "window not on the HDR monitor";
      std::sort(all.begin(), all.end());
      float p999 = all.empty() ? 0 : all[std::min(all.size() - 1, (size_t)(all.size() * 0.999))];
      std::vector<std::pair<long, int>> top; for (auto& kv : bright) top.push_back({kv.second, kv.first});
      std::sort(top.rbegin(), top.rend());
      char buf[1024];
      int k = snprintf(buf, sizeof buf, "%dx%d px | avg %.1f nits (Y), avg brightest-channel %.1f | max %.0f nits | 99.9%% %.0f | "
        "above paper white %.3f%% | above 300 nits %.3f%% | most common bright values:", xb - xa, yb - ya, sumY / n, sumMax / n,
        maxNits, p999, 100.0 * abovePaper / n, 100.0 * above300 / n);
      for (size_t i = 0; i < std::min<size_t>(4, top.size()); i++)
        k += snprintf(buf + k, sizeof buf - k, " %d nits (%.2f%%)", top[i].second, 100.0 * top[i].first / n);
      return buf;
    }
    return "no new frame (window not updating?)";
  }
};

static std::string firstLine(const char* path) {
  std::ifstream f(path); std::string s; std::getline(f, s);
  while (!s.empty() && (s.back() == '\r' || s.back() == ' ')) s.pop_back();
  return s;
}

int main(int argc, char** argv) {
  SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
  if (argc < 3) { printf("usage: hdrmeter once <title-part> [paper] | watch <status> <title-part> <out> [paper]\n"); return 2; }
  Meter mt; if (!mt.init()) { printf("no HDR output / duplication failed\n"); return 1; }
  std::string mode = argv[1];
  if (mode == "once") {
    Finder f{argv[2]}; EnumWindows(enumProc, (LPARAM)&f);
    if (!f.found) { printf("window not found\n"); return 1; }
    printf("%s\n", mt.capture(f.found, argc > 3 ? (float)atof(argv[3]) : 200.f).c_str());
    return 0;
  }
  if (mode == "watch" && argc >= 5) {
    const char* status = argv[2]; std::string part = argv[3]; const char* out = argv[4];
    float paper = argc > 5 ? (float)atof(argv[5]) : 200.f;
    std::string last;
    for (int i = 0; i < 20 * 60 * 30; i++) { // up to 30 minutes
      std::string s = firstLine(status);
      if (!s.empty() && s != last) {
        last = s;
        std::string line = s;
        if (s.rfind("menu", 0) == 0 || s.rfind("world_", 0) == 0) {
          Sleep(300);
          Finder f{part}; EnumWindows(enumProc, (LPARAM)&f);
          line += " | " + (f.found ? mt.capture(f.found, paper) : std::string("window not found"));
        }
        FILE* o = fopen(out, "a"); if (o) { fprintf(o, "%s\n", line.c_str()); fclose(o); }
        printf("%s\n", line.c_str()); fflush(stdout);
        if (s.rfind("finished", 0) == 0) return 0;
      }
      Sleep(50);
    }
    return 0;
  }
  printf("bad arguments\n"); return 2;
}
