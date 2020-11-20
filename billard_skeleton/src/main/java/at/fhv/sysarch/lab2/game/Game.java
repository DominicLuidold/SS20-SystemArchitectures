package at.fhv.sysarch.lab2.game;

import at.fhv.sysarch.lab2.physics.BallPocketedListener;
import at.fhv.sysarch.lab2.physics.BallsCollisionListener;
import at.fhv.sysarch.lab2.physics.ObjectsRestListener;
import at.fhv.sysarch.lab2.physics.PhysicsEngine;
import at.fhv.sysarch.lab2.rendering.Renderer;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseEvent;
import org.dyn4j.geometry.Vector2;

import java.util.*;

public class Game implements BallsCollisionListener, BallPocketedListener, ObjectsRestListener {
    public enum Player {
        PLAYER_ONE("Player 1"),
        PLAYER_TWO("Player 2");

        private final String name;

        Player(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private final Renderer renderer;
    private final PhysicsEngine engine;

    /* ## Mouse & Cue ## */
    private double mousePressedAtX;
    private double mousePressedAtY;

    /* ## Game relevant ## */
    private Player currentPlayer = Player.PLAYER_ONE;
    private int scorePlayer1 = 0;
    private int scorePlayer2 = 0;
    private int pocketedBallsInRound = 0;
    private boolean roundRunning = false;
    private boolean moveHandled = false;
    private boolean ballsMoving = false;
    private boolean foul = false;
    private final Set<Ball> pocketedBalls = new HashSet<>();

    /* ## White ball ## */
    private boolean whiteBallPocketed = false;
    private boolean whiteBallTouchedOtherBall = false;
    private Vector2 whiteBallPositionPreFoul;

    public Game(Renderer renderer, PhysicsEngine engine) {
        this.renderer = renderer;
        this.engine = engine;
        this.initWorld();

        engine.setBallsCollisionListener(this);
        engine.setBallPocketedListener(this);
        engine.setObjectsRestListener(this);
    }

    /* ###### Mouse & Cue related methods ###### */

    public void onMousePressed(MouseEvent e) {
        if (ballsMoving) {
            return;
        }

        double x = e.getX();
        double y = e.getY();

        double pX = this.renderer.screenToPhysicsX(x);
        double pY = this.renderer.screenToPhysicsY(y);

        mousePressedAtX = x;
        mousePressedAtY = y;
        this.renderer.setDrawingState(Renderer.CueDrawingState.PRESSED);
    }

    public void onMouseReleased(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();

        Point2D relativeMousePoint = calculateRelativePointOfMouse(x, y);
        double cueLength = calculateCueLength(relativeMousePoint);
        relativeMousePoint = relativeMousePoint.normalize();

        // TODO - Refactor with RayCasting
        if (!ballsMoving) {
            Ball.WHITE.getBody().applyImpulse(new Vector2(
                    relativeMousePoint.getX() * cueLength,
                    relativeMousePoint.getY() * cueLength
            ));
        }

        // Init cue drawing
        renderer.setCueCoordinates(
                relativeMousePoint.getX() * cueLength,
                relativeMousePoint.getY() * cueLength
        );
        renderer.setDrawingState(Renderer.CueDrawingState.RELEASED);
    }

    public void setOnMouseDragged(MouseEvent e) {
        if (ballsMoving) {
            return;
        }

        double x = e.getX();
        double y = e.getY();

        double pX = renderer.screenToPhysicsX(x);
        double pY = renderer.screenToPhysicsY(y);

        Point2D relativeMousePoint = calculateRelativePointOfMouse(x, y);
        double cueLength = calculateCueLength(relativeMousePoint);
        relativeMousePoint = relativeMousePoint.normalize();

        // Init cue drawing
        renderer.setCueCoordinates(
                relativeMousePoint.getX() * cueLength,
                relativeMousePoint.getY() * cueLength
        );
        renderer.setDrawingState(Renderer.CueDrawingState.DRAGGED);
    }

    // TODO - Explanation for calculations
    private double calculateCueLength(Point2D point) {
        double length = (point.magnitude() / 10) / 4;
        // Artificially limit length
        if (length > 10) {
            length = 10;
        }

        return length;
    }

    private Point2D calculateRelativePointOfMouse(double x, double y) {
        double deltaX = mousePressedAtX - x;
        double deltaY = mousePressedAtY - y;

        return new Point2D(deltaX, deltaY);
    }

    /* ###### Game methods ###### */

    private void placeBalls(List<Ball> balls, boolean ignoreTopSpot) {
        Collections.shuffle(balls);

        // positioning the billard balls IN WORLD COORDINATES: meters
        int row = 0;
        int col = 0;
        int colSize = 5;

        double y0 = -2 * Ball.Constants.RADIUS * 2;
        double x0 = -Table.Constants.WIDTH * 0.25 - Ball.Constants.RADIUS;

        for (Ball b : balls) {
            double y = y0 + (2 * Ball.Constants.RADIUS * row) + (col * Ball.Constants.RADIUS);
            double x = x0 + (2 * Ball.Constants.RADIUS * col);

            b.setPosition(x, y);
            b.getBody().setLinearVelocity(0, 0);
            engine.addBodyFromGame(b.getBody());
            renderer.addBall(b);

            row++;

            if (row == colSize) {
                row = 0;
                col++;
                colSize--;
            }

            if (ignoreTopSpot && 1 == colSize) {
                return;
            }
        }
    }

    private void initWorld() {
        List<Ball> balls = new ArrayList<>();

        for (Ball b : Ball.values()) {
            if (b == Ball.WHITE)
                continue;

            balls.add(b);
        }

        this.placeBalls(balls, false);

        setWhiteBallToDefaultPosition();
        engine.addBodyFromGame(Ball.WHITE.getBody());
        renderer.addBall(Ball.WHITE);

        Table table = new Table();
        engine.addBodyFromGame(table.getBody());
        renderer.setTable(table);
    }

    @Override
    public void onBallsCollide(Ball b1, Ball b2) {
        if (whiteBallTouchedOtherBall || moveHandled) {
            return;
        }

        if ((b1.isWhite() && !b2.isWhite() || (!b1.isWhite() && b2.isWhite()))) {
            whiteBallTouchedOtherBall = true;
        }
    }

    @Override
    public boolean onBallPocketed(Ball b) {
        // Prevent ball from spinning before removing
        b.getBody().setLinearVelocity(0, 0);

        if (b.isWhite()) {
            whiteBallPocketed = true;

            declareFoul("White ball has been pocketed");
            setWhiteBallToDefaultPosition();
        } else {
            pocketedBallsInRound++;
            updatePlayerScore(1);
            pocketedBalls.add(b);

            engine.removeBodyFromGame(b.getBody());
            renderer.removeBall(b);
        }

        // Return value not needed
        return false;
    }

    @Override
    public void onEndAllObjectsRest() {
        roundRunning = true;
        ballsMoving = true;
        moveHandled = false;
        whiteBallPocketed = false;
        clearMessages();
    }

    @Override
    public void onStartAllObjectsRest() {
        if (!roundRunning) {
            return;
        }

        if (!whiteBallPocketed && !whiteBallTouchedOtherBall && 0 == pocketedBallsInRound) {
            this.declareFoul("White ball has not touched other balls");
            setWhiteBallToPreFoulPosition();
        } else if (!whiteBallPocketed && whiteBallTouchedOtherBall && 0 == pocketedBallsInRound) {
            switchPlayers();
        }

        if (foul) {
            switchPlayers();
        }

        resetGameIfOnlyOneLeft();

        roundRunning = false;
        ballsMoving = false;
        moveHandled = true;
        foul = false;
        whiteBallTouchedOtherBall = false;
        pocketedBallsInRound = 0;
        whiteBallPositionPreFoul = Ball.WHITE.getBody().getTransform().getTranslation();
    }

    /* ###### Helper methods ###### */

    private void setWhiteBallToDefaultPosition() {
        Ball.WHITE.setPosition(Table.Constants.WIDTH * 0.25, 0);
        Ball.WHITE.getBody().setLinearVelocity(0, 0);
        whiteBallPositionPreFoul = Ball.WHITE.getBody().getTransform().getTranslation();
    }

    private void setWhiteBallToPreFoulPosition() {
        Ball.WHITE.setPosition(whiteBallPositionPreFoul.x, whiteBallPositionPreFoul.y);
        Ball.WHITE.getBody().setLinearVelocity(0, 0);
        whiteBallPositionPreFoul = Ball.WHITE.getBody().getTransform().getTranslation();
    }

    private void clearMessages() {
        renderer.setFoulMessage("");
        renderer.setActionMessage("");
    }

    private void switchPlayers() {
        if (currentPlayer.equals(Player.PLAYER_ONE)) {
            currentPlayer = Player.PLAYER_TWO;
        } else {
            currentPlayer = Player.PLAYER_ONE;
        }
        renderer.setActionMessage("Switching players, next player: " + currentPlayer.getName());
    }

    private void updatePlayerScore(int scoredPoint) {
        if (currentPlayer.equals(Player.PLAYER_ONE)) {
            scorePlayer1 += scoredPoint;
            renderer.setPlayer1Score(scorePlayer1);
        } else {
            scorePlayer2 += scoredPoint;
            renderer.setPlayer2Score(scorePlayer2);
        }
    }

    private void declareFoul(String message) {
        foul = true;

        renderer.setFoulMessage("Foul: " + message);
        updatePlayerScore(-1);
    }

    private void resetGameIfOnlyOneLeft() {
        if (pocketedBalls.size() >= 14) {
            List<Ball> balls = new ArrayList<>();

            for (Ball b : Ball.values()) {
                if (b == Ball.WHITE || !pocketedBalls.contains(b))
                    continue;

                balls.add(b);
            }

            setWhiteBallToDefaultPosition();
            placeBalls(balls, true);
        }
    }
}