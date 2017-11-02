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
 * Can be picked by the picker.
 ***************************************************************************/

#ifndef COLLIDER_H_
#define COLLIDER_H_

#include <vector>
#include "glm/glm.hpp"

#include "component.h"
#include "collider_shape_types.h"

namespace gvr {
class Collider;

/*
 * Information from a collision when a collider is picked.
 */
class ColliderData {
public:
    ColliderData(Collider* collider);
    ColliderData();

    void CopyHit(const ColliderData& src);

    SceneObject*    ObjectHit;
    Collider*       ColliderHit;
    bool            IsHit;
    glm::vec3       HitPosition;
    float           Distance;
    int             FaceIndex;
    glm::vec3       BarycentricCoordinates;
    glm::vec2       TextureCoordinates;
};

/*
 * Component attached to a SceneObject that provides
 * collision geometry and makes an object pickable.
 */
class Collider: public Component {
public:
    Collider() :Component(getComponentType()), pick_distance_(0) {}
    Collider(long long type) : Component(type), pick_distance_(0) {}

    virtual ~Collider() {}

    /*
     * Hit test the input ray against this collider.
     *
     * Casts the ray against the collider geometry and computes the hit
     * position (if any) in world space.
     *
     * @param rayStart      origin of the ray in world coordinates
     * @param rayDir        direction of the ray in world coordinates
     *
     * @returns ColliderData structure with hit point and distance from camera
     */
    virtual ColliderData isHit(const glm::vec3& rayStart, const glm::vec3& rayDir) = 0;

    virtual void set_owner_object(SceneObject*);

    virtual long shape_type() {
        return COLLIDER_SHAPE_UNKNOWN;
    }

    static long long getComponentType() {
        return COMPONENT_TYPE_COLLIDER;
    }

    void set_pick_distance(float dist) {
        pick_distance_ = dist;
    }

    float pick_distance() const {
        return pick_distance_;
    }
    static void transformRay(const glm::mat4& matrix, glm::vec3& rayStart, glm::vec3& rayDir);

protected:
    float pick_distance_;

    Collider(const Collider& collider);
    Collider(Collider&& collider);
    Collider& operator=(const Collider& collider);
    Collider& operator=(Collider&& collider);
};

inline ColliderData::ColliderData(Collider* collider) :
        ColliderHit(collider),
        IsHit(false),
        HitPosition(std::numeric_limits<float>::infinity()),
        Distance((std::numeric_limits<float>::infinity())),
        FaceIndex(-1),
        BarycentricCoordinates(-1.0f),
        TextureCoordinates(-1.0f)
{
    if (collider != NULL)
    {
        ObjectHit = collider->owner_object();
    }
    else
    {
        ObjectHit = NULL;
    }
}

inline ColliderData::ColliderData() :
        ColliderHit(NULL),
        ObjectHit(NULL),
        IsHit(false),
        HitPosition(std::numeric_limits<float>::infinity()),
        Distance(std::numeric_limits<float>::infinity()),
        FaceIndex(-1),
        BarycentricCoordinates(-1.0f),
        TextureCoordinates(-1.0f)
{
}

inline void ColliderData::CopyHit(const ColliderData& src)
{
    IsHit = src.IsHit;
    HitPosition = src.HitPosition;
    Distance = src.Distance;
}

inline bool compareColliderData(ColliderData i, ColliderData j) {
    return i.Distance < j.Distance;
}

}
#endif
