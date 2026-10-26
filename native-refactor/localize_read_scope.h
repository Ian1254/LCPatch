#pragma once

#include <cstdio>
#include <cstring>
#include <unistd.h>

namespace lcpatch {

// Only the synchronous text loader may see replacements. In particular, a
// checksum on another thread must never inherit a text load's permission.
inline thread_local unsigned localizeReadDepth = 0;

class LocalizeReadScope {
public:
    LocalizeReadScope() { ++localizeReadDepth; }
    ~LocalizeReadScope() { --localizeReadDepth; }
    LocalizeReadScope(const LocalizeReadScope &) = delete;
    LocalizeReadScope &operator=(const LocalizeReadScope &) = delete;
};

inline bool buildLocalizeRedirect(const char *path, const char *runtimeDirectory,
                                  char *output, size_t outputSize) {
    if (!localizeReadDepth || !path || !*path || !runtimeDirectory || !output || !outputSize ||
        std::strstr(path, runtimeDirectory) == path || std::strchr(path, '\\') ||
        std::strstr(path, "/../") || std::strstr(path, "/./")) return false;
    constexpr char marker[] = "/files/Assets/Resources_moved/Localize/";
    const char *found = std::strstr(path, marker);
    const char *language = found ? found + sizeof(marker) - 1 :
        (std::strncmp(path, "Localize/", 9) == 0 ? path + 9 : nullptr);
    if (!language || (std::strncmp(language, "en/", 3) != 0 &&
                      std::strncmp(language, "jp/", 3) != 0 &&
                      std::strncmp(language, "kr/", 3) != 0)) return false;
    const char *relative = language + 3;
    if (!*relative || *relative == '/' || std::strstr(relative, "../") ||
        std::strstr(relative, "./") || std::strstr(relative, "//")) return false;
    const char *name = std::strrchr(relative, '/');
    name = name ? name + 1 : relative;
    const char *prefix = language[0] == 'e' ? "EN_" : language[0] == 'j' ? "JP_" : "KR_";
    const size_t length = std::strlen(name);
    if (length <= 8 || std::strncmp(name, prefix, 3) != 0 ||
        std::strcmp(name + length - 5, ".json") != 0) return false;
    const int written = std::snprintf(output, outputSize, "%s/%s", runtimeDirectory, relative);
    return written > 0 && static_cast<size_t>(written) < outputSize && access(output, R_OK) == 0;
}

} // namespace lcpatch
