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

#include <cstdint>
#include <limits>

namespace lcpatch {

// Only unconditional AArch64 B is an ABI-preserving tail-call veneer. BL and
// conditional branches are deliberately not accepted as resolver exports.
inline bool aarch64TailBranchTarget(uintptr_t address, uint32_t instruction, uintptr_t &target) {
    if ((address & 3U) || (instruction & 0xfc000000U) != 0x14000000U) return false;
    int64_t immediate = instruction & 0x03ffffffU;
    if (immediate & 0x02000000LL) immediate -= 0x04000000LL;
    const int64_t displacement = immediate * 4;
    if (!displacement) return false;
    if (displacement > 0) {
        const uintptr_t distance = static_cast<uintptr_t>(displacement);
        if (address > std::numeric_limits<uintptr_t>::max() - distance) return false;
        target = address + distance;
    } else {
        const uintptr_t distance = static_cast<uintptr_t>(-displacement);
        if (address < distance) return false;
        target = address - distance;
    }
    return (target & 3U) == 0;
}

inline bool aarch64BranchLandingHint(uint32_t instruction) {
    return instruction == 0xd503245fU || instruction == 0xd503249fU ||
        instruction == 0xd50324dfU || instruction == 0xd503201fU;
}

struct SharedIcallEntry {
    uintptr_t entry = 0;
    const char *reason = nullptr;
    bool pending = false;
};

// The reader must admit both executable and readable bytes in the same image.
// Keep this selection independent of Dobby so unsafe/unsupported veneers can
// be checked without executing code from a game binary.
template <typename Reader>
SharedIcallEntry findSharedIcallResolver(uintptr_t publicEntry, Reader read) {
    uintptr_t address = publicEntry;
    uintptr_t seen[4]{};
    bool followed = false;
    for (unsigned depth = 0; depth < 4; ++depth) {
        for (unsigned i = 0; i < depth; ++i) if (seen[i] == address)
            return {0, "resolver_veneer_cycle", false};
        seen[depth] = address;
        uint32_t instruction = 0;
        if (!read(address, instruction)) return {0, "resolver_branch_outside_image", false};
        if (!instruction) return {0, "resolver_code_not_ready", true};
        if (aarch64BranchLandingHint(instruction)) {
            if (address > std::numeric_limits<uintptr_t>::max() - 4U)
                return {0, "resolver_branch_outside_image", false};
            address += 4;
            continue;
        }
        uintptr_t next = 0;
        if (aarch64TailBranchTarget(address, instruction, next)) {
            address = next;
            followed = true;
            continue;
        }
        return followed ? SharedIcallEntry{address, nullptr, false} :
            SharedIcallEntry{0, "public_resolver_not_tail_veneer", false};
    }
    return {0, "resolver_veneer_depth_exceeded", false};
}

} // namespace lcpatch

namespace lcpatch {
template <typename Reader>
uintptr_t textAccessorBody(uintptr_t entry, Reader read) {
    uintptr_t seen[4]{};
    for (unsigned depth = 0; depth < 4; ++depth) {
        if (!entry || (entry & 3U)) return 0;
        for (unsigned i = 0; i < depth; ++i) if (seen[i] == entry) return 0;
        seen[depth] = entry;
        uint32_t word = 0;
        if (!read(entry, word) || !word) return 0;
        if (aarch64BranchLandingHint(word)) {
            if (entry > std::numeric_limits<uintptr_t>::max()-4U) return 0;
            entry += 4;
            continue;
        }
        uintptr_t next = 0;
        if (aarch64TailBranchTarget(entry, word, next)) { entry = next; continue; }
        return entry;
    }
    return 0;
}
// Match the runtime metadata's text->bytes call, then require a tail-dispatch
// wrapper. Unrecognized code keeps official bytes. No game addresses are stored.
template <typename Reader>
uintptr_t findTextBytesReturn(uintptr_t text, uintptr_t bytes, Reader read) {
    if (!text || !bytes || (text & 3U) || (bytes & 3U)) return 0;
    const uintptr_t bytesEntry = bytes;
    text = textAccessorBody(text, read);
    bytes = textAccessorBody(bytes, read);
    if (!text || !bytes) return 0;
    uintptr_t found = 0;
    for (unsigned offset = 0; offset < 96; offset += 4) {
        if (text > std::numeric_limits<uintptr_t>::max()-offset) return 0;
        const auto at = text+offset;
        uint32_t word = 0;
        if (!read(at, word) || !word) return 0;
        if ((word & 0xfc000000U) == 0x94000000U) {
            uintptr_t target = 0;
            if (aarch64TailBranchTarget(at, (word & 0x03ffffffU) | 0x14000000U, target) && (target == bytesEntry || target == bytes)) {
                if (found || at > std::numeric_limits<uintptr_t>::max()-4U) return 0;
                found = at+4;
            }
        }
        if (word == 0xd65f03c0U || (word & 0xfc000000U) == 0x14000000U) break;
    }
    if (!found) return 0;
    for (unsigned offset = 0; offset < 128; offset += 4) {
        if (bytes > std::numeric_limits<uintptr_t>::max()-offset) return 0;
        uint32_t word = 0;
        if (!read(bytes+offset, word) || !word) return 0;
        if (word == 0xd65f03c0U || (word & 0xfc000000U) == 0x14000000U) return 0;
        if ((word & 0xfffffc1fU) == 0xd61f0000U) {
            const auto reg = (word >> 5) & 31U;
            return reg < 30 ? found : 0;
        }
    }
    return 0;
}
inline bool textAssetDisplayResource(const std::string &key) {
    return key == "LoginUIText" || key == "FileDownloadDesc" || key == "BattleHint" ||
        key == "BattleHint_AbnorBattle" || key == "BattleHint_NormalBattle";
}
}
