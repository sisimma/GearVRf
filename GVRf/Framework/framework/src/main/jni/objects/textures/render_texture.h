/* Copyright 2015 Samsung Electronics Co., LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/***************************************************************************
 * A frame buffer object.
 ***************************************************************************/

#ifndef RENDER_TEXTURE_H_
#define RENDER_TEXTURE_H_

#include "gl/gl_render_buffer.h"
#include "gl/gl_frame_buffer.h"
#include "util/gvr_parameters.h"
#include "objects/textures/base_texture.h"
#include "util/gvr_gl.h"

namespace gvr {

class RenderTexture: public Texture {
public:
    RenderTexture(int width, int height, GLTexture* tex);
    explicit RenderTexture(int width, int height, GLenum target);
    explicit RenderTexture(int width, int height, int sample_count, GLenum target);
    explicit RenderTexture(int width, int height, GLenum target, int sample_count,
                                     int jcolor_format, int jdepth_format, bool resolve_depth,
                                     int* texture_parameters);
    void createArrayRenderTexture(int, int,bool);
    void create2DRenderTexture(int jcolor_format, int jdepth_format, bool resolve_depth);
    virtual ~RenderTexture() {
        delete renderTexture_gl_render_buffer_;
        delete renderTexture_gl_frame_buffer_;
        delete renderTexture_gl_color_buffer_;
        delete renderTexture_gl_resolve_buffer_;

        if (0 != renderTexture_gl_pbo_) {
            glDeleteBuffers(1, &renderTexture_gl_pbo_);
        }
    }

    void initialize(int width, int height) {
        glGenBuffers(1, &renderTexture_gl_pbo_);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, renderTexture_gl_pbo_);
        glBufferData(GL_PIXEL_PACK_BUFFER, width_ * height_ * 4, 0, GL_DYNAMIC_READ);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);

        readback_started_ = false;
    }

    GLenum getTarget() const {
        return target_;
    }

    GLuint getFrameBufferId() const {
        return renderTexture_gl_frame_buffer_->id();
    }
    GLuint getReadBufferId(){
        if(renderTexture_gl_read_buffer == NULL)
            renderTexture_gl_read_buffer = new GLFrameBuffer();

        return  renderTexture_gl_read_buffer->id();
    }
    GLuint getDepthBufferId() { return renderTexture_gl_render_buffer_->id(); }

    void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, renderTexture_gl_frame_buffer_->id());
    }

    int width() const {
        return width_;
    }

    int height() const {
        return height_;
    }

    void setBackgroundColor(float r, float g, float b);
    void useStencil(bool useFlag)   { use_stencil_ = useFlag; }
    virtual void beginRendering();
    virtual void endRendering();

    // Start to read back texture in the background. It can be optionally called before
    // readRenderResult() to read pixels asynchronously. This function returns immediately.
    void startReadBack(int layer);

    // Copy data in pixel buffer to client memory. This function is synchronous. When
    // it returns, the pixels have been copied to PBO and then to the client memory.
    bool readRenderResult(uint32_t *readback_buffer, long capacity, int eye);

private:
    RenderTexture(const RenderTexture& render_texture);
    RenderTexture(RenderTexture&& render_texture);
    RenderTexture& operator=(const RenderTexture& render_texture);
    RenderTexture& operator=(RenderTexture&& render_texture);
    void generateRenderTextureNoMultiSampling(int jdepth_format,GLenum depth_format, int width, int height);
    void generateRenderTextureEXT(int sample_count,int jdepth_format,GLenum depth_format, int width, int height);
    void generateRenderTexture(int sample_count, int jdepth_format, GLenum depth_format, int width,
            int height, int jcolor_format);
    void invalidateFrameBuffer(GLenum target, bool is_fbo, const bool color_buffer, const bool depth_buffer);

protected:
    GLuint frameBufferDepthTextureId;
    int width_;
    int height_;
    int sample_count_;
    bool use_stencil_;
    float back_color_[3];
    GLRenderBuffer* renderTexture_gl_render_buffer_ = nullptr;// This is actually depth buffer.
    GLFrameBuffer* renderTexture_gl_frame_buffer_ = nullptr;
    GLFrameBuffer* renderTexture_gl_resolve_buffer_ = nullptr;
    GLRenderBuffer* renderTexture_gl_color_buffer_ = nullptr;// This is only for multisampling case
    GLFrameBuffer* renderTexture_gl_read_buffer;                                 // when resolveDepth is on.
    GLuint render_texture_gl_texture_ = 0;
    GLuint renderTexture_gl_pbo_ = 0;
    GLenum target_;
    bool readback_started_;          // set by startReadBack()
};

class RenderTextureArray : public RenderTexture
{
public:
    RenderTextureArray(int width, int height, int numLayers);
    bool bindFrameBuffer(int layerIndex);
    bool bindTexture(int gl_location, int texIndex);
    virtual void beginRendering();

protected:
    int     mNumLayers;
};
}
#endif
