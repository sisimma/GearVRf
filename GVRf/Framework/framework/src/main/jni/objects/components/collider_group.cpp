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
 * Can hold multiple colliders attached to a scene object.
 ***************************************************************************/

#include "collider_group.h"

#include "objects/scene_object.h"
#include "glm/gtc/matrix_inverse.hpp"


namespace gvr {

ColliderGroup::ColliderGroup() :
        Collider(ColliderGroup::getComponentType()) {
}

ColliderGroup::~ColliderGroup() {
}

void ColliderGroup::addCollider(Collider* collider) {
    colliders_.push_back(collider);
}

void ColliderGroup::removeCollider(Collider* collider) {
    colliders_.erase(std::remove(colliders_.begin(), colliders_.end(), collider),
            colliders_.end());
}

ColliderData ColliderGroup::isHit(const glm::vec3& rayStart, const glm::vec3& rayDir) {
    ColliderData finalHit(reinterpret_cast<Collider*>(this));
    SceneObject* ownerObject = owner_object();

    hit_ = glm::vec3(std::numeric_limits<float>::infinity());
    if (nullptr != ownerObject) {
        Transform* transform = ownerObject->transform();
        finalHit.ObjectHit = ownerObject;
        if (nullptr != transform) {
            glm::mat4 model_inverse = glm::affineInverse(transform->getModelMatrix());
            glm::vec3 O(rayStart);
            glm::vec3 D(rayDir);

            transformRay(model_inverse, O, D);
            for (auto it = colliders_.begin(); it != colliders_.end(); ++it) {
                ColliderData currentHit = (*it)->isHit(O, D);
                if (currentHit.IsHit && (currentHit.Distance < finalHit.Distance)) {
                    hit_ = currentHit.HitPosition;
                    finalHit.CopyHit(currentHit);
                }
            }
        }
    }
    return finalHit;
}
}
