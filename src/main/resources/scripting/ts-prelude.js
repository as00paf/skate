// Evaluated once per script context. Turns the Kotlin ScriptContext into an idiomatic JS object.
globalThis.exports = {};
globalThis.module = { exports: globalThis.exports };

globalThis.__skateHandler = function (name) {
    const fn = globalThis.exports[name] !== undefined ? globalThis.exports[name] : globalThis[name];
    return typeof fn === 'function' ? fn : null;
};

globalThis.__skateWrap = function (c) {
    function rigidBody(b) {
        return {
            get mass() { return b.getMass(); },
            set mass(v) { b.setMass(v); },
            get linearVelocity() { return b.getLinearVelocity(); },
            set linearVelocity(v) { b.setLinearVelocity(v); },
            get angularVelocity() { return b.getAngularVelocity(); },
            set angularVelocity(v) { b.setAngularVelocity(v); },
        };
    }

    function entity(e) {
        const t = e.getTransform();
        return {
            get id() { return e.getId(); },
            get name() { return e.getName(); },
            set name(v) { e.setName(v); },
            transform: { position: t.getPosition(), rotation: t.getRotation(), scale: t.getScale() },
            getComponent(type) {
                const comp = e.component(type);
                return comp === null || comp === undefined ? null : (type === 'RigidBody' ? rigidBody(comp) : comp);
            },
            hasComponent(type) { return e.component(type) !== null; },
        };
    }

    const scene = c.getScene(), input = c.getInput(), physics = c.getPhysics();
    const events = c.getEvents(), time = c.getTime(), log = c.getLog();
    const props = c.getProperties(), state = c.getState();

    return {
        entity: entity(c.getEntity()),
        scene: {
            find(name) { const e = scene.find(name); return e ? entity(e) : null; },
            findAll(name) { return Array.from(scene.findAll(name)).map(entity); },
        },
        input: {
            isKeyDown: (k) => input.isKeyDown(k),
            isKeyPressed: (k) => input.isKeyPressed(k),
            isKeyReleased: (k) => input.isKeyReleased(k),
            isMouseButtonDown: (b) => input.isMouseButtonDown(b),
            action: (n) => input.action(n),
        },
        physics: {
            get gravity() { return physics.getGravity(); },
            raycast: (from, to) => { const d = physics.raycast(from, to); return d === null ? null : d; },
        },
        events: {
            on: (name, handler) => events.on(name, (payload) => { handler(payload); }),
            emit: (name, payload) => events.emit(name, payload === undefined ? null : payload),
        },
        time: {
            get deltaTime() { return time.getDeltaTime(); },
            get fixedDeltaTime() { return time.getFixedDeltaTime(); },
            get elapsed() { return time.getElapsed(); },
        },
        log: {
            info: (m) => log.info(String(m)),
            warn: (m) => log.warn(String(m)),
            error: (m) => log.error(String(m)),
        },
        properties: props,
        state: {
            get: (k) => state.get(k),
            set: (k, v) => { state.put(k, v); },
        },
        get reloadCount() { return c.getReloadCount(); },
    };
};
