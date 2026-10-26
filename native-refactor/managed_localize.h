#pragma once

#include <atomic>
#include <cstdint>
#include <thread>
#include "localize_read_scope.h"

namespace lcpatch {

// Opaque managed references. CancellationToken is one reference-sized value;
// the runtime installer checks its value size and each method's signature.
struct CancellationToken { void *source; };
using LoadSync = void *(*)(void *, void *, const void *);
using LoadAsync = void (*)(void *, int32_t, const void *);
using ReadText = void *(*)(void *, const void *);
using ReadTextAsync = void *(*)(void *, CancellationToken, const void *);

struct ManagedLocalize {
    LoadSync loadSync = nullptr;
    LoadAsync loadAsync = nullptr;
    ReadText readText = nullptr;
    ReadTextAsync readTextAsync = nullptr;
    int32_t (*stringLength)(void *) = nullptr;
    const char16_t *(*stringChars)(void *) = nullptr;
    void *(*stringNew)(const char *) = nullptr;
    void (*onRedirect)(const char *, const char *) = nullptr;
    const char *runtimeDirectory = nullptr;
    std::atomic<bool> ready{false};
    std::atomic<bool> installing{false};
};
inline ManagedLocalize managedLocalize;

inline void waitForTrampolines() {
    while (managedLocalize.installing.load(std::memory_order_acquire)) std::this_thread::yield();
}

inline void *selectManagedPath(void *path) {
    auto &api = managedLocalize;
    if (!api.ready.load(std::memory_order_acquire) || !localizeReadDepth || !path) return path;
    const int32_t length = api.stringLength(path);
    char original[4096], replacement[4096];
    if (length <= 0 || static_cast<size_t>(length) >= sizeof(original)) return path;
    const char16_t *chars = api.stringChars(path);
    if (!chars) return path;
    for (int32_t i = 0; i < length; ++i) {
        if (!chars[i] || chars[i] > 0x7f) return path;
        original[i] = static_cast<char>(chars[i]);
    }
    original[length] = '\0';
    if (!buildLocalizeRedirect(original, api.runtimeDirectory, replacement, sizeof(replacement))) return path;
    void *selected = api.stringNew(replacement);
    if (!selected) return path;
    if (api.onRedirect) api.onRedirect(original, replacement);
    return selected;
}

inline void *hookedLoadSync(void *self, void *name, const void *method) {
    waitForTrampolines();
    LocalizeReadScope scope;
    return managedLocalize.loadSync(self, name, method);
}

inline void hookedLoadAsync(void *self, int32_t concurrent, const void *method) {
    waitForTrampolines();
    LocalizeReadScope scope;
    managedLocalize.loadAsync(self, concurrent, method);
}

inline void *hookedReadText(void *path, const void *method) {
    waitForTrampolines();
    return managedLocalize.readText(selectManagedPath(path), method);
}

inline void *hookedReadTextAsync(void *path, CancellationToken token, const void *method) {
    waitForTrampolines();
    // Substitute before the Task captures the path; later worker threads do
    // not inherit the scope, and validation on those threads sees originals.
    return managedLocalize.readTextAsync(selectManagedPath(path), token, method);
}

} // namespace lcpatch
