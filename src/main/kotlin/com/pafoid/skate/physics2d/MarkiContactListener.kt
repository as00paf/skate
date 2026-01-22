package physics2d

import marki.GameObject
import org.jbox2d.callbacks.ContactImpulse
import org.jbox2d.callbacks.ContactListener
import org.jbox2d.collision.Manifold
import org.jbox2d.collision.WorldManifold
import org.jbox2d.dynamics.contacts.Contact
import org.joml.Vector2f

class MarkiContactListener: ContactListener {

    override fun beginContact(contact: Contact) {
        val goA = contact.fixtureA.userData as GameObject
        val goB = contact.fixtureB.userData as GameObject
        val worldManifold = WorldManifold()
        contact.getWorldManifold(worldManifold)
        val aNormal = Vector2f(worldManifold.normal.x, worldManifold.normal.y)
        val bNormal = Vector2f(aNormal).negate()

        goA.getAllComponents().forEach { c ->
            c.beginCollision(goB, contact, aNormal)
        }

        goB.getAllComponents().forEach { c ->
            c.beginCollision(goA, contact, bNormal)
        }
    }

    override fun endContact(contact: Contact) {
        val goA = contact.fixtureA.userData as GameObject
        val goB = contact.fixtureB.userData as GameObject
        val worldManifold = WorldManifold()
        contact.getWorldManifold(worldManifold)
        val aNormal = Vector2f(worldManifold.normal.x, worldManifold.normal.y)
        val bNormal = Vector2f(aNormal).negate()

        goA.getAllComponents().forEach { c ->
            c.endCollision(goB, contact, aNormal)
        }

        goB.getAllComponents().forEach { c ->
            c.endCollision(goA, contact, bNormal)
        }
    }

    override fun preSolve(contact: Contact, manifold: Manifold) {
        val goA = contact.fixtureA.userData as GameObject
        val goB = contact.fixtureB.userData as GameObject
        val worldManifold = WorldManifold()
        contact.getWorldManifold(worldManifold)
        val aNormal = Vector2f(worldManifold.normal.x, worldManifold.normal.y)
        val bNormal = Vector2f(aNormal).negate()

        goA.getAllComponents().forEach { c ->
            c.preSolve(goB, contact, aNormal)
        }

        goB.getAllComponents().forEach { c ->
            c.preSolve(goA, contact, bNormal)
        }
    }

    override fun postSolve(contact: Contact, p1: ContactImpulse) {
        val goA = contact.fixtureA.userData as GameObject
        val goB = contact.fixtureB.userData as GameObject
        val worldManifold = WorldManifold()
        contact.getWorldManifold(worldManifold)
        val aNormal = Vector2f(worldManifold.normal.x, worldManifold.normal.y)
        val bNormal = Vector2f(aNormal).negate()

        goA.getAllComponents().forEach { c ->
            c.postSolve(goB, contact, aNormal)
        }

        goB.getAllComponents().forEach { c ->
            c.postSolve(goA, contact, bNormal)
        }
    }
}