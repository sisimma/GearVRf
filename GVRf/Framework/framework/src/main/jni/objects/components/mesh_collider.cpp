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
 * Eye pointee made by a mesh.
 ***************************************************************************/

#include <limits>

#include "glm/glm.hpp"
#include "glm/gtc/matrix_inverse.hpp"
#include "glm/gtx/intersect.hpp"
#include "util/gvr_log.h"
#include "mesh_collider.h"
#include "render_data.h"
#include "objects/mesh.h"
#include "objects/bounding_volume.h"
#include "objects/mesh.h"
#include "objects/scene_object.h"
#include "sphere_collider.h"

namespace gvr
{
    MeshCollider::MeshCollider(Mesh* mesh) :
            Collider(getComponentType()), mesh_(mesh), pickCoordinates_(false),
            useMeshBounds_(false)
    {
    }

    MeshCollider::MeshCollider(Mesh* mesh, bool pickCoordinates) :
            Collider(getComponentType()), mesh_(mesh), pickCoordinates_(pickCoordinates),
            useMeshBounds_(false)
    {
    }

    MeshCollider::MeshCollider(bool useMeshBounds) :
            Collider(getComponentType()), mesh_(NULL), pickCoordinates_(false),
            useMeshBounds_(useMeshBounds)
    {
    }

    MeshCollider::~MeshCollider()
    {}

/*
 * Hit test the triangles in the mesh against the input ray.
 *
 * The ray is converted into mesh coordinates by transforming it
 * with the concatenation of the view_matrix and the model matrix
 * of the scene object which owns the collider.
 * The hit point computed is in local coordinates (same coordinate
 * space as the mesh vertices).
 *
 * @param view_matrix   camera view matrix (inverse of camera model matrix)
 * @param rayStart      origin of the ray in world coordinates
 * @param rayDir        direction of the ray in world coordinates
 *
 * @returns EyePointData structure with hit point and distance from camera
 */
    ColliderData MeshCollider::isHit(const glm::vec3& rayStart, const glm::vec3& rayDir)
    {
        Mesh* mesh = mesh_;
        bool pickCoordinates = pickCoordinates_;
        RenderData* rd = NULL;
        SceneObject* owner = owner_object();
        glm::vec3 O(rayStart);
        glm::vec3 D(rayDir);

        /*
         * If the scene object this collider is attached to also
         * has a transform, compute the model view matrix by
         * concatenating the scene object"s model matrix with the
         * input view matrix.
         */
        if (owner != NULL)
        {
            RenderData* rd = owner->render_data();
            glm::mat4 model_matrix = owner->transform()->getModelMatrix();
            glm::mat4 model_inverse = glm::affineInverse(model_matrix);

            transformRay(model_inverse, O, D);
            if ((mesh == NULL) && (rd != NULL))
            {
                mesh = rd->mesh();
            }
        }
        /*
         * Compute the point where the ray penetrates the mesh in
         * the coordinate space of the mesh. Then apply the
         * model view matrix to put it into camera coordinates.
         */
        ColliderData data;
        if (mesh != NULL)
        {
            if (useMeshBounds_)
            {
                const BoundingVolume& bounds = mesh->getBoundingVolume();
                data = MeshCollider::isHit(bounds, O, D);
            } else
            {
                data = MeshCollider::isHit(*mesh, O, D, pickCoordinates);
            }
            if (data.IsHit)

            {
                data.Distance = glm::distance(O, data.HitPosition);
                data.ColliderHit = this;
                data.ObjectHit = owner;
            }
        }
        return data;
    }


/**
 * Efficient means of solving Barycentric coordinates by Christer Ericson/John Calsbeek found at
 * https://gamedev.stackexchange.com/questions/23743/whats-the-most-efficient-way-to-find-barycentric-coordinates
 * @param p         3D point lying on triangle formed by points a, b, and c.
 * @param a         the first of the three points forming the triangle
 * @param b         the second of the three points forming the triangle
 * @param c         the third of the three points forming the triangle
 * @param coords    the vec3 that will hold the resulting Barcentric coordinates of p
 */
    static void
    calcBarycentric(const glm::vec3& p, const glm::vec3& a, const glm::vec3& b, const glm::vec3& c,
                    glm::vec3& coords)
    {
        glm::vec3 v0 = b - a, v1 = c - a, v2 = p - a;
        float d00 = (float) glm::dot(v0, v0);
        float d01 = (float) glm::dot(v0, v1);
        float d11 = (float) glm::dot(v1, v1);
        float d20 = (float) glm::dot(v2, v0);
        float d21 = (float) glm::dot(v2, v1);
        float denom = d00 * d11 - d01 * d01;
        coords.y = (d11 * d20 - d01 * d21) / denom;
        coords.z = (d00 * d21 - d01 * d20) / denom;
        coords.x = 1.0f - coords.y - coords.z;
    }

/**
 * Sets the Barycentric coordinates corresponding to the HitPoint on the mesh
 * @param mesh          the Mesh of the object that was collided with
 * @param colliderData  the ColliderData holding the HitPoint which will also store the Barycentric
 * coordinates
 */
    static void populateBarycentricCoords(const Mesh& mesh, ColliderData& colliderData)
    {
        const std::vector<glm::vec3>& vertices = mesh.vertices();

        glm::vec3 v1(vertices[mesh.triangles()[colliderData.FaceIndex * 3]]);
        glm::vec3 v2(vertices[mesh.triangles()[colliderData.FaceIndex * 3 + 1]]);
        glm::vec3 v3(vertices[mesh.triangles()[colliderData.FaceIndex * 3 + 2]]);

        calcBarycentric(colliderData.HitPosition, v1, v2, v3, colliderData.BarycentricCoordinates);
    }

/**
 * Sets the Barycentric coordinates, UV coordinates, and normal corresponding to the HitPoint on the
 * mesh
 * @param mesh          the Mesh of the object that was collided with
 * @param colliderData  the ColliderData holding the HitPoint which will also store the UV coordinates
 *                      and the surface normal
 */
    static void populateSurfaceCoords(const Mesh& mesh, ColliderData& colliderData)
    {
        populateBarycentricCoords(mesh, colliderData);
        try
        {
            const std::vector<glm::vec2>& texCoords = mesh.getVec2Vector(
                    "a_texcoord"); //may not exist
            glm::vec2 u1(texCoords[mesh.triangles()[colliderData.FaceIndex * 3]]);
            glm::vec2 u2(texCoords[mesh.triangles()[colliderData.FaceIndex * 3 + 1]]);
            glm::vec2 u3(texCoords[mesh.triangles()[colliderData.FaceIndex * 3 + 2]]);

            glm::vec3 n1(mesh.normals()[mesh.triangles()[colliderData.FaceIndex * 3]]);
            glm::vec3 n2(mesh.normals()[mesh.triangles()[colliderData.FaceIndex * 3 + 1]]);
            glm::vec3 n3(mesh.normals()[mesh.triangles()[colliderData.FaceIndex * 3 + 2]]);

            colliderData.TextureCoordinates = u1 * colliderData.BarycentricCoordinates.x
                                              + u2 * colliderData.BarycentricCoordinates.y
                                              + u3 * colliderData.BarycentricCoordinates.z;
            colliderData.NormalCoordinates = n1 * colliderData.BarycentricCoordinates.x
                                             + n2 * colliderData.BarycentricCoordinates.y
                                             + n3 * colliderData.BarycentricCoordinates.z;
        }
        catch (const std::string& warning)
        {
            LOGW("%s", warning.c_str());
        }
        catch (...)
        {
            LOGE("An unexpected error occurred while calculating texture coordinates.");
        }
    }

/*
 * Hit test the input ray against the triangles of the given mesh.
 * @param mesh  mesh to hit test
 * @param rayStart  start of the pick ray in model coordinates
 * @param rayDir    direction of the pick ray in model coordinates
 * @return ColliderData with the hit point and distance in model coordinates
 */
    ColliderData
    MeshCollider::isHit(const Mesh& mesh, const glm::vec3& rayStart, const glm::vec3& rayDir,
                        bool pickCoordinates)
    {
        const std::vector<glm::vec3>& vertices = mesh.vertices();
        ColliderData data;
        if (vertices.size() > 0)
        {
            for (int i = 0; i < mesh.triangles().size(); i += 3)
            {
                glm::vec3 V1(vertices[mesh.triangles()[i]]);
                glm::vec3 V2(vertices[mesh.triangles()[i + 1]]);
                glm::vec3 V3(vertices[mesh.triangles()[i + 2]]);

                /*
                 * Compute the point where the ray penetrates the mesh in
                 * the coordinate space of the mesh. The hit point will
                 * be in mesh coordinates as will the distance.
                 */
                glm::vec3 hitPos;
                float distance = rayTriangleIntersect(hitPos, rayStart, rayDir, V1, V2, V3);
                if ((distance > 0) && (distance < data.Distance))
                {
                    data.IsHit = true;
                    data.HitPosition = hitPos;
                    data.Distance = distance;
                    data.FaceIndex = i / 3;
                }
            }
            if (pickCoordinates && data.IsHit && mesh.hasAttribute("a_texcoord"))
            {
                populateSurfaceCoords(mesh, data);
            }

        }
        return data;
    }

/*
 * Determine if the ray penetrates an axially aligned bounding box
 * @param bounds    bounding volume (radius ignored, corners of box are used)
 * @param rayStart  origin of ray in model coordinates
 * @param rayDir    direction of ray in model coordinates
 */
    ColliderData MeshCollider::isHit(const BoundingVolume& bounds, const glm::vec3& rayStart,
                                     const glm::vec3& rayDir)
    {
        ColliderData data;
        glm::vec3 hitPos;
        if (bounds.intersect(hitPos, rayStart, rayDir))
        {
            data.IsHit = true;
            data.HitPosition = hitPos;
            data.Distance = glm::distance(rayStart, hitPos);
        }
        return data;
    }

    float MeshCollider::rayTriangleIntersect(glm::vec3& hitPos, const glm::vec3& rayStart,
                                             const glm::vec3& rayDir,
                                             const glm::vec3& V1, const glm::vec3& V2,
                                             const glm::vec3& V3)
    {
        glm::vec3 e1(V2 - V1);
        glm::vec3 e2(V3 - V1);
        glm::vec3 P = glm::cross(rayDir, e2);
        glm::vec3 T(glm::vec3(rayStart) - V1);
        float det = glm::dot(e1, P);
        const float EPSILON = 0.00001f;

        if (det > -EPSILON && det < EPSILON)
        {
            return -1;
        }

        float inv_det = 1.0f / det;
        float u = glm::dot(T, P) * inv_det;

        if (u < 0.0f || u > 1.0f)
        {
            return -1;
        }

        glm::vec3 Q = glm::cross(T, e1);
        float v = glm::dot(glm::vec3(rayDir), Q) * inv_det;

        if (v < 0.0f || (u + v) > 1.0f)
        {
            return -1;
        }

        float t = glm::dot(e2, Q) * inv_det;

        if (t > EPSILON)
        {
            hitPos = (1.0f - u - v) * V1 + u * V2 + v * V3;
            return t;
        }
        return -1;
    }

}