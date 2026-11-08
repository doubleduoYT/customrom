// HGSoft Overuse Controller v0.6 - Windows per-app CPU controller.
// The selected application is launched SUSPENDED, attached to a Job Object,
// then resumed. This helper never modifies unrelated processes or system CPU policy.
#define UNICODE
#define _UNICODE
#define _WIN32_WINNT 0x0602
#include <windows.h>
#include <shellapi.h>
#include <cwctype>
#include <cstdio>
#include <string>
#include <algorithm>

static void status(const std::wstring& file, const wchar_t* value) {
    FILE* f = _wfopen(file.c_str(), L"wb");
    if (!f) return;
    fwprintf(f, L"%ls\n", value);
    fclose(f);
}
static bool isAllowedExe(const std::wstring& path) {
    if (path.size() < 5 || path.size() > 32700) return false;
    std::wstring lowered = path;
    std::transform(lowered.begin(), lowered.end(), lowered.begin(),
                   [](wchar_t c){ return static_cast<wchar_t>(towlower(c)); });
    if (lowered.substr(lowered.size()-4) != L".exe") return false;
    if (lowered.rfind(L"\\\\",0)==0 || lowered.rfind(L"\\??\\",0)==0) return false;
    wchar_t windir[MAX_PATH] = {};
    GetWindowsDirectoryW(windir, MAX_PATH);
    std::wstring win(windir);
    std::transform(win.begin(), win.end(), win.begin(),
                   [](wchar_t c){return static_cast<wchar_t>(towlower(c));});
    if (!win.empty() && lowered.rfind(win+L"\\", 0)==0) return false;
    DWORD attrs=GetFileAttributesW(path.c_str());
    return attrs!=INVALID_FILE_ATTRIBUTES && !(attrs&FILE_ATTRIBUTE_DIRECTORY);
}
static bool applyCpuRate(HANDLE job, int percent) {
    JOBOBJECT_CPU_RATE_CONTROL_INFORMATION cap = {};
    cap.ControlFlags = JOB_OBJECT_CPU_RATE_CONTROL_ENABLE | JOB_OBJECT_CPU_RATE_CONTROL_HARD_CAP;
    cap.CpuRate = static_cast<DWORD>(std::max(10, std::min(100, percent)) * 100);
    return SetInformationJobObject(job, JobObjectCpuRateControlInformation, &cap, sizeof(cap)) != FALSE;
}
static int capForStage(int stage) {
    static const int caps[] = {100,90,80,70,60,50};
    return caps[std::max(0,std::min(5,stage))];
}
int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR, int) {
    int argc=0;
    LPWSTR* argv=CommandLineToArgvW(GetCommandLineW(),&argc);
    if (!argv || argc!=5) { if (argv) LocalFree(argv); return 2; }
    std::wstring config(argv[1]), report(argv[2]), exe(argv[4]);
    DWORD parentPid=wcstoul(argv[3],nullptr,10);
    LocalFree(argv);
    if (!isAllowedExe(exe)) { status(report,L"ERROR_INVALID_EXE"); return 3; }

    HANDLE parent=OpenProcess(SYNCHRONIZE,FALSE,parentPid);
    if (!parent) { status(report,L"ERROR_PARENT"); return 4; }
    HANDLE job=CreateJobObjectW(nullptr,nullptr);
    if (!job) { status(report,L"ERROR_JOB"); CloseHandle(parent); return 5; }

    // Verify support BEFORE launching the target process.
    if (!applyCpuRate(job,100)) {
        status(report,L"ERROR_CPU_JOB_UNSUPPORTED");
        CloseHandle(job); CloseHandle(parent); return 6;
    }

    STARTUPINFOW si={};
    si.cb=sizeof(si);
    PROCESS_INFORMATION pi={};
    std::wstring dir=exe.substr(0,exe.find_last_of(L"\\/"));
    if (!CreateProcessW(exe.c_str(),nullptr,nullptr,nullptr,FALSE,
                        CREATE_SUSPENDED, nullptr,
                        dir.empty()?nullptr:dir.c_str(),&si,&pi)) {
        status(report,L"ERROR_CREATE_PROCESS");
        CloseHandle(job); CloseHandle(parent); return 7;
    }
    if (!AssignProcessToJobObject(job,pi.hProcess)) {
        status(report,L"ERROR_ASSIGN_PROCESS");
        TerminateProcess(pi.hProcess,1);  // Process has never started; safe cleanup.
        CloseHandle(pi.hThread); CloseHandle(pi.hProcess);
        CloseHandle(job); CloseHandle(parent); return 8;
    }
    if (ResumeThread(pi.hThread)==(DWORD)-1) {
        status(report,L"ERROR_RESUME");
        TerminateProcess(pi.hProcess,1);
        CloseHandle(pi.hThread); CloseHandle(pi.hProcess);
        CloseHandle(job); CloseHandle(parent); return 9;
    }
    CloseHandle(pi.hThread);
    status(report,L"RUNNING");

    int lastCap=100;
    while (WaitForSingleObject(parent,0)==WAIT_TIMEOUT &&
           WaitForSingleObject(pi.hProcess,0)==WAIT_TIMEOUT) {
        int enabled=0,stage=0,paused=0,running=0;
        FILE* f=_wfopen(config.c_str(),L"rb");
        if (f) {
            bool valid=fscanf(f,"%d %d %d %d",&running,&stage,&paused,&enabled)==4;
            fclose(f);
            if (valid) {
                const int cap = (running && enabled && !paused) ? capForStage(stage) : 100;
                if (cap!=lastCap) {
                    if (!applyCpuRate(job,cap)) {
                        status(report,L"ERROR_SET_RATE");
                        break; // Job closes below; target keeps running unrestricted.
                    }
                    lastCap=cap;
                }
                wchar_t buffer[64]={};
                swprintf(buffer,64,L"RUNNING_%d_PERCENT",cap);
                status(report,buffer);
            }
        }
        Sleep(1000);
    }
    // Closing the job HANDLE removes the CPU quota without killing the app.
    CloseHandle(job);
    CloseHandle(pi.hProcess);
    CloseHandle(parent);
    status(report,L"STOPPED");
    return 0;
}
