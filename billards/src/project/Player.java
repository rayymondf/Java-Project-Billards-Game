package project;

class Player {
    final String name;
    BallGroup group = BallGroup.UNASSIGNED;

    Player(String name) {
        this.name = name;
    }

    boolean owns(Ball ball) {
        return ball != null && ball.group == group;
    }

    String groupLabel() {
        return group.label();
    }
}
