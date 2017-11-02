 #   
 # Copyright 2015 Samsung Electronics Co., LTD
 #
 # Licensed under the Apache License, Version 2.0 (the "License");
 # you may not use this file except in compliance with the License.
 # You may obtain a copy of the License at
 #
 #     http://www.apache.org/licenses/LICENSE-2.0
 #
 # Unless required by applicable law or agreed to in writing, software
 # distributed under the License is distributed on an "AS IS" BASIS,
 # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 # See the License for the specific language governing permissions and
 # limitations under the License.
 #
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := assimp
LOCAL_SRC_FILES := ../prebuilt/$(TARGET_ARCH_ABI)/libassimp.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := jnlua
LOCAL_SRC_FILES := ../prebuilt/$(TARGET_ARCH_ABI)/libjnlua.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := jav8
LOCAL_SRC_FILES := ../prebuilt/$(TARGET_ARCH_ABI)/libjav8.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE := gvrf

ROOT_PATH := $(LOCAL_PATH)/../jni
FILE_LIST := $(wildcard $(ROOT_PATH)/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)

LOCAL_C_INCLUDES += $(ROOT_PATH)/contrib/assimp
LOCAL_C_INCLUDES +=	$(ROOT_PATH)/contrib/assimp/include
LOCAL_C_INCLUDES +=	$(ROOT_PATH)/contrib/assimp/include/Compiler

LOCAL_C_INCLUDES += $(ROOT_PATH)/contrib/jassimp
# Uncomment for logs
# LOCAL_CFLAGS += -DANDROID -DJNI_LOG
FILE_LIST := $(wildcard $(ROOT_PATH)/contrib/jassimp/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)

LOCAL_C_INCLUDES += $(ROOT_PATH)/contrib
LOCAL_C_INCLUDES += $(ROOT_PATH)/util
LOCAL_C_INCLUDES += $(ROOT_PATH)

FILE_LIST := $(wildcard $(ROOT_PATH)/contrib/glm/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/contrib/glm/detail/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(LOCAL_PATH)/contrib/glm/gtc/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(LOCAL_PATH)/contrib/glm/gtx/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)

FILE_LIST := $(wildcard $(ROOT_PATH)/eglextension/msaa/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/eglextension/tiledrendering/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/engine/importer/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/engine/exporter/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/engine/picker/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/engine/renderer/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/engine/memory/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/gl/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/objects/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/objects/components/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/objects/textures/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/shaders/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/shaders/material/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/shaders/posteffect/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/util/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)
FILE_LIST := $(wildcard $(ROOT_PATH)/vulkan/*.cpp)
LOCAL_SRC_FILES += $(FILE_LIST)

LOCAL_SHARED_LIBRARIES += assimp
LOCAL_SHARED_LIBRARIES += jnlua
LOCAL_SHARED_LIBRARIES += jav8

ifeq ($(TARGET_ARCH_ABI),$(filter $(TARGET_ARCH_ABI), armeabi-v7a x86))
#LOCAL_ARM_NEON  := true
endif

## CPP flags are already defined in cflags.mk.
#LOCAL_CPPFLAGS += -fexceptions -frtti -std=c++11 -D__GXX_EXPERIMENTAL_CXX0X__ -mhard-float -D_NDK_MATH_NO_SOFTFP=1
#for NO_RTTI and softFP
LOCAL_CPPFLAGS += -fexceptions -std=c++1y -D__GXX_EXPERIMENTAL_CXX0X__
ifdef ARM64
LOCAL_CPPFLAGS += -DARM64
endif
LOCAL_CFLAGS := -Wattributes

# include ld libraries defined in oculus's cflags.mk
#LOCAL_LDLIBS += -ljnigraphics -lm_hard
#softFP
LOCAL_LDLIBS += -ljnigraphics -llog -lGLESv3 -lEGL -lz -landroid
#LOCAL_LDLIBS += -ldl -lbinder -lgui

include $(BUILD_SHARED_LIBRARY)