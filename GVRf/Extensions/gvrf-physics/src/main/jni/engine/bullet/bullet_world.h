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
 * Bullet implementation of 3D world
 ***************************************************************************/

#ifndef BULLET_WORLD_H_
#define BULLET_WORLD_H_

#include "../physics_common.h"
#include "../physics_world.h"

#include <utility>
#include <map>

class btDynamicsWorld;
class btCollisionConfiguration;
class btCollisionDispatcher;
class btSequentialImpulseConstraintSolver;
class btBroadphaseInterface;

namespace gvr {

class PhysicsConstraint;
class PhysicsRigidBody;

class BulletWorld : public PhysicsWorld {
 public:
    BulletWorld();

    ~BulletWorld();

    void addConstraint(PhysicsConstraint *constraint);

    void removeConstraint(PhysicsConstraint *constraint);

    void addRigidBody(PhysicsRigidBody *body);

    void addRigidBody(PhysicsRigidBody *body, int collisiontype, int collidesWith);

    void removeRigidBody(PhysicsRigidBody *body);

    void step(float timeStep);

    void listCollisions(std::list <ContactPoint> &contactPoints);

    void setGravity(float x, float y, float z);

    void setGravity(glm::vec3 gravity);

    PhysicsVec3 getGravity() const;

    static std::mutex worldLock;

 private:
    void initialize();

    void finalize();

 private:
    std::map<std::pair <long,long>, ContactPoint> prevCollisions;
    btDynamicsWorld *mPhysicsWorld;
    btCollisionConfiguration *mCollisionConfiguration;
    btCollisionDispatcher *mDispatcher;
    btSequentialImpulseConstraintSolver *mSolver;
    btBroadphaseInterface *mOverlappingPairCache;
    //void (*gTmpFilter)(); // btNearCallback
    //int gNearCallbackCount = 0;
    //void *gUserData = 0;
};

}

#endif /* BULLET_WORLD_H_ */
