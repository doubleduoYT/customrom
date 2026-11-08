// Overuse Controller v0.4.2: safe, click-through Windows desktop overlay.
// Works as a companion process to the Godot Windows app.
// No screen capture, hooks, elevation, or system-setting changes.
#define UNICODE
#define _UNICODE
#include <windows.h>
#include <shellapi.h>
#include <cstdio>
#include <string>
#include <cwchar>
#include <algorithm>

static HWND g_hwnd = nullptr;
static int g_stage = 0;
static int g_strength = 65;
static bool g_color = true;
static bool g_masks = true;
static COLORREF g_palette[] = {
    RGB(0, 0, 0), RGB(55, 78, 110), RGB(71, 57, 99),
    RGB(47, 92, 92), RGB(105, 74, 67), RGB(94, 54, 78)
};

LRESULT CALLBACK OverlayWindowProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if (msg == WM_NCHITTEST) return HTTRANSPARENT;
    if (msg == WM_MOUSEACTIVATE) return MA_NOACTIVATE;
    if (msg == WM_ERASEBKGND) return 1;
    if (msg == WM_PAINT) {
        PAINTSTRUCT ps;
        HDC dc = BeginPaint(hwnd, &ps);
        RECT rc;
        GetClientRect(hwnd, &rc);
        // Color-keyed background enables mask-only mode without darkening the entire desktop.
        HBRUSH bg = CreateSolidBrush(g_color ? g_palette[g_stage] : RGB(1, 2, 3));
        FillRect(dc, &rc, bg);
        DeleteObject(bg);
        if (g_masks) {
            HBRUSH mask = CreateSolidBrush(RGB(12, 25, 39));
            int width = std::max(1L, rc.right - rc.left);
            for (int i = 0; i < g_stage; ++i) {
                int x = (width * (i + 1)) / (g_stage + 1);
                RECT strip = {x, 0, x + 8 + i * 7, rc.bottom};
                FillRect(dc, &strip, mask);
            }
            DeleteObject(mask);
        }
        EndPaint(hwnd, &ps);
        return 0;
    }
    if (msg == WM_DESTROY) {
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wParam, lParam);
}

static bool ReadConfig(const std::wstring &file, int &running, int &stage,
                       int &strength, int &color, int &masks, int &paused) {
    FILE *f = _wfopen(file.c_str(), L"rb");
    if (!f) return false;
    bool ok = fscanf(f, "%d %d %d %d %d %d",
                     &running, &stage, &strength, &color, &masks, &paused) == 6;
    fclose(f);
    return ok;
}

static void WriteIdle(const std::wstring &file, DWORD millis) {
    FILE *f = _wfopen(file.c_str(), L"wb");
    if (f) {
        fprintf(f, "%lu\n", static_cast<unsigned long>(millis));
        fclose(f);
    }
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int) {
    int argc = 0;
    LPWSTR *argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (!argv || argc < 4) {
        if (argv) LocalFree(argv);
        return 2;
    }
    const std::wstring configPath(argv[1]);
    const std::wstring idlePath(argv[2]);
    DWORD parentPid = static_cast<DWORD>(wcstoul(argv[3], nullptr, 10));
    LocalFree(argv);

    HANDLE parent = OpenProcess(SYNCHRONIZE, FALSE, parentPid);
    if (!parent) return 3;

    WNDCLASSEXW wc = {};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = OverlayWindowProc;
    wc.hInstance = instance;
    wc.lpszClassName = L"HGSoftOveruseDesktopOverlay";
    wc.hbrBackground = nullptr;
    if (!RegisterClassExW(&wc)) {
        CloseHandle(parent);
        return 4;
    }

    const int x = GetSystemMetrics(SM_XVIRTUALSCREEN);
    const int y = GetSystemMetrics(SM_YVIRTUALSCREEN);
    const int width = GetSystemMetrics(SM_CXVIRTUALSCREEN);
    const int height = GetSystemMetrics(SM_CYVIRTUALSCREEN);
    g_hwnd = CreateWindowExW(
        WS_EX_TOPMOST | WS_EX_LAYERED | WS_EX_TRANSPARENT |
        WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE,
        wc.lpszClassName, L"HGSoft Overuse Overlay",
        WS_POPUP, x, y, width, height, nullptr, nullptr, instance, nullptr);
    if (!g_hwnd) {
        CloseHandle(parent);
        return 5;
    }

    ShowWindow(g_hwnd, SW_HIDE);
    bool visible = false;
    int prior_stage = -1;
    int prior_alpha = -1;
    bool prior_color = false;
    bool prior_masks = false;

    // The helper never captures pixels or records keyboard/mouse contents.
    // Only Windows idle duration is shared with the Godot app.
    while (WaitForSingleObject(parent, 0) == WAIT_TIMEOUT) {
        MSG msg;
        while (PeekMessageW(&msg, nullptr, 0, 0, PM_REMOVE)) {
            if (msg.message == WM_QUIT) goto shutdown;
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }

        LASTINPUTINFO li = {};
        li.cbSize = sizeof(li);
        DWORD idle = GetLastInputInfo(&li) ? (GetTickCount() - li.dwTime) : 0;
        WriteIdle(idlePath, idle);

        int running = 0, stage = 0, strength = 65, color = 1, masks = 1, paused = 0;
        if (ReadConfig(configPath, running, stage, strength, color, masks, paused)) {
            g_stage = std::max(0, std::min(5, stage));
            g_strength = std::max(0, std::min(100, strength));
            g_color = color != 0;
            g_masks = masks != 0;
            bool wantVisible = running != 0 && paused == 0 && g_stage > 0 &&
                               g_strength > 0 && (g_color || g_masks);
            if (wantVisible) {
                // Global alpha remains < 0.7; input always passes through.
                int alpha = std::min(160, (30 + g_stage * 24) * g_strength / 100);
                if (!visible) {
                    ShowWindow(g_hwnd, SW_SHOWNOACTIVATE);
                    visible = true;
                }
                if (alpha != prior_alpha) {
                    SetLayeredWindowAttributes(g_hwnd, RGB(1,2,3),
                                               static_cast<BYTE>(alpha), LWA_ALPHA | LWA_COLORKEY);
                    prior_alpha = alpha;
                }
                if (g_stage != prior_stage || g_color != prior_color || g_masks != prior_masks) {
                    InvalidateRect(g_hwnd, nullptr, TRUE);
                    prior_stage = g_stage;
                    prior_color = g_color;
                    prior_masks = g_masks;
                }
                SetWindowPos(g_hwnd, HWND_TOPMOST, x, y, width, height,
                             SWP_NOACTIVATE | SWP_SHOWWINDOW);
            } else if (visible) {
                ShowWindow(g_hwnd, SW_HIDE);
                visible = false;
                prior_stage = -1;
            }
        }
        Sleep(300);
    }
shutdown:
    DestroyWindow(g_hwnd);
    CloseHandle(parent);
    return 0;
}
