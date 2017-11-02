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
 * Renders a GL_TEXTURE_EXTERNAL_OES texture.
 ***************************************************************************/

#include "oes_shader.h"

#include "gl/gl_program.h"
#include "objects/material.h"
#include "objects/mesh.h"
#include "objects/components/render_data.h"
#include "util/gvr_gl.h"
#include "engine/renderer/renderer.h"

namespace gvr {
static const char VERTEX_SHADER[] =
        "#version 300 es\n"
        "in vec4 a_position;\n"
        "in vec2 a_texcoord;\n"
        "uniform mat4 u_mvp;\n"
        "out vec2 v_texcoord;\n"
        "void main() {\n"
        "  v_texcoord = a_texcoord.xy;\n"
        "  gl_Position = u_mvp * a_position;\n"
        "}\n";

static const char VERTEX_SHADER_MULTIVIEW[] =
        "#version 300 es\n"
        "#extension GL_OVR_multiview2 : enable\n"
        "layout(num_views = 2) in;\n"
        "uniform uint u_render_mask;\n"
        "uniform mat4 u_mvp_[2];\n"
        "in vec3 a_position;\n"
        "in vec2 a_texcoord;\n"
        "out vec2 v_texcoord;\n"
        "void main() {\n"
        "  v_texcoord = a_texcoord.xy;\n"
        "  bool render_mask = (u_render_mask & (gl_ViewID_OVR + uint(1))) > uint(0) ? true : false;\n"
        "  mat4 mvp = u_mvp_[gl_ViewID_OVR];"
        " if(!render_mask)\n"
                "mvp = mat4(0.0);\n"     //  if render_mask is not set for particular eye, dont render that object
         "  gl_Position = mvp * vec4(a_position,1.0);\n"
        "}\n";

static const char FRAGMENT_SHADER[] =
        "#version 300 es\n"
        "#extension GL_OES_EGL_image_external : enable\n"
        "#extension GL_OES_EGL_image_external_essl3 : enable\n"
                "precision highp float;\n"
                "uniform samplerExternalOES u_texture;\n"
                "uniform vec3 u_color;\n"
                "uniform float u_opacity;\n"
                "in vec2 v_texcoord;\n"
                "out vec4 out_color;\n"
                "void main()\n"
                "{\n"
                "  vec4 color = texture(u_texture, v_texcoord);"
                "  out_color = vec4(color.r * u_color.r * u_opacity, color.g * u_color.g * u_opacity, color.b * u_color.b * u_opacity, color.a * u_opacity);\n"
                "}\n";


OESShader::OESShader() :
        u_mvp_(0),
        u_texture_(0),
        u_color_(0),
        u_opacity_(0) {}

void OESShader::programInit(RenderState* rstate) {
    if (rstate->is_multiview) {
        const char* extensions = (const char*) glGetString(GL_EXTENSIONS);
        if (std::strstr(extensions, "GL_OES_EGL_image_external") == NULL) {
            LOGE("GLSL does not support GL_OES_EGL_image_external, try with disabling multiview \n");
        }

        program_ = new GLProgram(VERTEX_SHADER_MULTIVIEW,FRAGMENT_SHADER);
        u_mvp_ = glGetUniformLocation(program_->id(), "u_mvp_[0]");
        u_render_mask_ = glGetUniformLocation(program_->id(), "u_render_mask");
    } else {
        program_ = new GLProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        u_mvp_ = glGetUniformLocation(program_->id(), "u_mvp");
    }

    u_texture_ = glGetUniformLocation(program_->id(), "u_texture");
    u_color_ = glGetUniformLocation(program_->id(), "u_color");
    u_opacity_ = glGetUniformLocation(program_->id(), "u_opacity");
}

OESShader::~OESShader() {
    delete program_;
}

void OESShader::render(RenderState* rstate, RenderData* render_data, Material* material) {
    if(program_ == nullptr)
        programInit(rstate);

    Texture* texture = material->getTexture("main_texture");
    glm::vec3 color = material->getVec3("color");
    float opacity = material->getFloat("opacity");

    if (texture->getTarget() != GL_TEXTURE_EXTERNAL_OES) {
        std::string error = "OESShader::render : texture with wrong target";
        throw error;
    }

    glUseProgram(program_->id());
    if (rstate->is_multiview) {
        glUniformMatrix4fv(u_mvp_, 2, GL_FALSE, glm::value_ptr(rstate->uniforms.u_mvp_[0]));
        glUniform1ui(u_render_mask_,render_data->render_mask());
    } else {
        glUniformMatrix4fv(u_mvp_, 1, GL_FALSE, glm::value_ptr(rstate->uniforms.u_mvp));
    }

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(texture->getTarget(), texture->getId());
    glUniform1i(u_texture_, 0);
    glUniform3f(u_color_, color.r, color.g, color.b);
    glUniform1f(u_opacity_, opacity);
    checkGLError("OESShader::render");
}

}

