package cam72cam.immersiverailroading.library;

public enum PhysicalMaterials {
    STEEL,
    CAST_IRON,
    WOOD,
    ;

    public float staticFriction(PhysicalMaterials other) {
        return this.friction(other, false);
    }

    private float friction(PhysicalMaterials other, boolean kinetic) {
        // unless otherwise specified: https://structx.com/Material_Properties_005a.html

        if (this.match(STEEL, STEEL, this, other)) {
            // assume slightly dirty / non-ideal surfaces
            return kinetic ? 0.42f : 0.7f;
        }
        if (this.match(STEEL, CAST_IRON, this, other)) {
            return kinetic ? 0.25f : 0.4f;
        }
        if (this.match(STEEL, WOOD, this, other)) {
            return kinetic ? 0.2f : 0.6f;
        }

        return 0;
    }

    private boolean match(PhysicalMaterials materialA, PhysicalMaterials materialB, PhysicalMaterials matchA, PhysicalMaterials matchB) {
        return materialA == matchA && materialB == matchB ||
                materialA == matchB && materialB == matchA;
    }

    public float kineticFriction(PhysicalMaterials other) {
        return this.friction(other, true);
    }
}
