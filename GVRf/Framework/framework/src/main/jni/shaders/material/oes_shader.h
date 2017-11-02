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

#ifndef OES_SHADER_H_
#define OES_SHADER_H_

#include "shaderbase.h"

namespace gvr {
class RenderData;
class Material;
struct RenderState;

class OESShader: public ShaderBase {
public:
    OESShader();
    virtual ~OESShader();
    virtual void render(RenderState* rstate, RenderData* render_data, Material* material);
private:
    OESShader(const OESShader& oes_shader) = delete;
    OESShader(OESShader&& oes_shader) = delete;
    OESShader& operator=(const OESShader& oes_shader) = delete;
    OESShader& operator=(OESShader&& oes_shader) = delete;

    void programInit(RenderState*);

private:
    GLint u_render_mask_;
    GLint u_mvp_;
    GLint u_texture_;
    GLint u_color_;
    GLint u_opacity_;
};

}

#endif
