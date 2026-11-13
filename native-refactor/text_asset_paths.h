#pragma once
#include <string>
#include <unordered_map>

namespace lcpatch {
inline bool safeAssetPath(const std::string &path) {
    if (path.empty() || path.front() == '/' || path.find("\\") != std::string::npos ||
        path.find_first_of("\t\r\n:") != std::string::npos || path.size() > 1024) return false;
    size_t begin = 0;
    do {
        const auto end = path.find('/', begin);
        const auto part = path.substr(begin, end - begin);
        if (part.empty() || part == "." || part == "..") return false;
        if (end == std::string::npos) break;
        begin = end + 1;
    } while (true);
    return path.size() > 5 && path.compare(path.size() - 5, 5, ".json") == 0;
}
inline std::string assetKey(std::string name) {
    if (name.size() > 5 && name.compare(name.size() - 5, 5, ".json") == 0) name.resize(name.size() - 5);
    if (name.size() > 3 && (name.compare(0, 3, "JP_") == 0 || name.compare(0, 3, "KR_") == 0 ||
        name.compare(0, 3, "EN_") == 0)) name.erase(0, 3);
    return name;
}
inline std::unordered_map<std::string, std::string> parseAssetIndex(const std::string &text) {
    std::unordered_map<std::string, std::string> result;
    std::unordered_map<std::string, bool> ambiguous;
    size_t begin = 0;
    while (begin < text.size() && result.size() < 30000) {
        const auto end = text.find('\n', begin);
        const auto line = text.substr(begin, end - begin);
        const auto tab = line.find('\t');
        if (tab != std::string::npos) {
            const auto key = line.substr(0, tab), path = line.substr(tab + 1);
            if (safeAssetPath(path) && !key.empty() && key.size() <= 1024 &&
                key.find_first_of("\t\r\n\\:") == std::string::npos && !ambiguous[key]) {
                const auto previous = result.find(key);
                if (previous != result.end() && previous->second != path) {
                    result.erase(previous); ambiguous[key] = true;
                } else result[key] = path;
            }
        }
        if (end == std::string::npos) break;
        begin = end + 1;
    }
    return result;
}
}
