
#include "dobby/dobby_internal.h"
#include <unistd.h>
#include <sys/mman.h>
#include <stdlib.h>
#include <string.h>

#if !defined(__APPLE__)
PUBLIC int DobbyCodePatch(void *address, uint8_t *buffer, uint32_t buffer_size) {
#if defined(__ANDROID__) || defined(__linux__)
  if (!address || !buffer || buffer_size == 0)
    return -1;
  const size_t page_size = (size_t)sysconf(_SC_PAGESIZE);
  const uintptr_t patch_page = ALIGN_FLOOR(address, page_size);
  const uintptr_t patch_end = ALIGN_CEIL((uintptr_t)address + buffer_size, page_size);
  const size_t patch_size = patch_end - patch_page;
  uint8_t *backup = (uint8_t *)malloc(buffer_size);
  if (!backup)
    return -1;
  memcpy(backup, address, buffer_size);

  // change page permission as rwx
  if (mprotect((void *)patch_page, patch_size, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
    free(backup);
    return -1;
  }

  // patch buffer
  memcpy(address, buffer, buffer_size);

  addr_t clear_start_ = (addr_t)address;
  ClearCache((void *)clear_start_, (void *)(clear_start_ + buffer_size));
  // restore page permission; roll back the bytes if the transition is rejected
  if (mprotect((void *)patch_page, patch_size, PROT_READ | PROT_EXEC) != 0) {
    memcpy(address, backup, buffer_size);
    ClearCache((void *)clear_start_, (void *)(clear_start_ + buffer_size));
    mprotect((void *)patch_page, patch_size, PROT_READ | PROT_EXEC);
    free(backup);
    return -1;
  }
  free(backup);
#endif
  return 0;
}

#endif
