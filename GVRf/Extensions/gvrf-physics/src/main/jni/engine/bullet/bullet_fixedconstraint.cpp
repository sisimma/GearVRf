//
// Created by Juliana Figueira on 5/9/17.
//

#include "bullet_fixedconstraint.h"
#include "bullet_rigidbody.h"
#include <BulletDynamics/ConstraintSolver/btFixedConstraint.h>

#include <android/log.h>

namespace gvr {

BulletFixedConstraint::BulletFixedConstraint(PhysicsRigidBody* rigidBodyB) {
    mFixedConstraint = 0;
    mRigidBodyB = reinterpret_cast<BulletRigidBody*>(rigidBodyB);
    mBreakingImpulse = SIMD_INFINITY;
}

BulletFixedConstraint::~BulletFixedConstraint() {
    if (0 != mFixedConstraint) {
        delete mFixedConstraint;
    }
}

void BulletFixedConstraint::set_owner_object(SceneObject* obj) {
    if (obj == owner_object())
    {
        return;
    }
    Component::set_owner_object(obj);
    if (obj)
    {
        onAttach(obj);
    }
}

void BulletFixedConstraint::setBreakingImpulse(float impulse) {
    if (0 != mFixedConstraint) {
        mFixedConstraint->setBreakingImpulseThreshold(impulse);
    }
    else {
        mBreakingImpulse = impulse;
    }
}

float BulletFixedConstraint::getBreakingImpulse() const {
    if (0 != mFixedConstraint) {
        return mFixedConstraint->getBreakingImpulseThreshold();
    }
    else {
        return mBreakingImpulse;
    }
}

void BulletFixedConstraint::onAttach(SceneObject* owner) {
    btRigidBody* rbA = ((BulletRigidBody*)this->owner_object()->
            getComponent(COMPONENT_TYPE_PHYSICS_RIGID_BODY))->getRigidBody();

    mFixedConstraint = new btFixedConstraint(*rbA, *mRigidBodyB->getRigidBody(),
                                             mRigidBodyB->getRigidBody()->getWorldTransform(),
                                             rbA->getWorldTransform());
    mFixedConstraint->setBreakingImpulseThreshold(mBreakingImpulse);
}

}