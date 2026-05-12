package project;

enum BallGroup {
    CUE,
    SOLID,
    STRIPE,
    EIGHT,
    UNASSIGNED;

    BallGroup opposite() {
        if (this == SOLID) {
            return STRIPE;
        }
        if (this == STRIPE) {
            return SOLID;
        }
        return UNASSIGNED;
    }

    String label() {
        switch (this) {
            case SOLID:
                return "Solids";
            case STRIPE:
                return "Stripes";
            case EIGHT:
                return "8-ball";
            default:
                return "Open";
        }
    }
}
