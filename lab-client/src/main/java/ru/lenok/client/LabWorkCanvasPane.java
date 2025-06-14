package ru.lenok.client;

import javafx.animation.AnimationTimer;
import javafx.animation.TranslateTransition;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;
import ru.lenok.common.models.LabWork;
import ru.lenok.common.models.LabWorkWithKey;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public class LabWorkCanvasPane extends Pane {
    private LanguageManager lm = LanguageManager.getInstance();
    private final ObservableList<LabWorkWithKey> labWorks;
    private final Map<Long, Color> ownerColorMap = new HashMap<>();
    private final Map<LabWorkWithKey, Group> visualized = new HashMap<>();
    private final Random rand = new Random();
    private LabWorkWithKey selectedLabWork;
    private Consumer<LabWorkWithKey> onLabWorkSelected;

    private final Locale defaultLocale = Locale.getDefault();
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(defaultLocale);
    private final NumberFormat integerFormat = NumberFormat.getIntegerInstance(defaultLocale);
    Locale locale = Locale.getDefault();

    public LabWorkCanvasPane(ObservableList<LabWorkWithKey> labWorks) {
        this.labWorks = labWorks;
        widthProperty().addListener(observable -> redrawAll());
        heightProperty().addListener(observable -> redrawAll());
        redrawAll();
    }

    public void setOnLabWorkSelected(Consumer<LabWorkWithKey> listener) {
        this.onLabWorkSelected = listener;
    }

    public void highlight(LabWorkWithKey lw) {
        selectedLabWork = lw;
        visualized.forEach((lab, group) -> {
            Circle circle = (Circle) group.getChildren().get(0);
            if (lab.equals(lw)) {
                circle.setStroke(Color.DEEPSKYBLUE);
                circle.setStrokeWidth(4);
                circle.setStrokeType(StrokeType.OUTSIDE);
            } else {
                circle.setStroke(Color.BLACK);
                circle.setStrokeWidth(1);
            }
        });
    }

    private void addWithAnimation(LabWorkWithKey lw) {
        double canvasWidth = getWidth();
        double canvasHeight = getHeight();
        double radius = Math.max(lw.getMinimalPoint() / 2.0, 10);

        Color color = getColorForOwner(lw.getOwnerId());

        Circle circle = new Circle(radius, color);
        circle.setStroke(Color.BLACK);

        Text label = new Text(String.valueOf(lw.getOwnerId()));
        label.setFont(Font.font(12));
        label.setFill(Color.BLACK);
        label.setTranslateX(-5);
        label.setTranslateY(4);

        Group group = new Group(circle, label);
        group.setTranslateX(rand.nextDouble() * (canvasWidth - 2 * radius));
        group.setTranslateY(rand.nextDouble() * (canvasHeight - 2 * radius));

        getChildren().add(group);
        visualized.put(lw, group);

        Tooltip tooltip = new Tooltip(buildTooltipText(lw));
        tooltip.setShowDelay(Duration.millis(200));
        tooltip.setShowDuration(Duration.minutes(1));
        tooltip.setHideDelay(Duration.millis(200));
        Tooltip.install(group, tooltip);

        double[] velocity = {(rand.nextDouble() * 200 - 100) * 10, (rand.nextDouble() * 200 - 100) * 10};
        long startTime = System.currentTimeMillis();

        AnimationTimer timer = new AnimationTimer() {
            long lastUpdate = 0;

            @Override
            public void handle(long now) {
                if (lastUpdate == 0) {
                    lastUpdate = now;
                    return;
                }

                double dt = (now - lastUpdate) / 1_000_000_000.0;
                lastUpdate = now;

                double x = group.getTranslateX() + velocity[0] * dt;
                double y = group.getTranslateY() + velocity[1] * dt;

                if (x <= 0 || x + radius * 2 >= canvasWidth) {
                    velocity[0] *= -1;
                    x = Math.max(0, Math.min(x, canvasWidth - radius * 2));
                }
                if (y <= 0 || y + radius * 2 >= canvasHeight) {
                    velocity[1] *= -1;
                    y = Math.max(0, Math.min(y, canvasHeight - radius * 2));
                }

                for (Map.Entry<LabWorkWithKey, Group> entry : visualized.entrySet()) {
                    if (entry.getValue() == group) continue;

                    Group other = entry.getValue();
                    Circle otherCircle = (Circle) other.getChildren().get(0);
                    double dx = other.getTranslateX() - x;
                    double dy = other.getTranslateY() - y;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    double rSum = radius + otherCircle.getRadius();

                    if (dist < rSum && dist > 0) {
                        double overlap = rSum - dist;
                        double nx = dx / dist;
                        double ny = dy / dist;

                        x -= nx * overlap / 2;
                        y -= ny * overlap / 2;
                        other.setTranslateX(other.getTranslateX() + nx * overlap / 2);
                        other.setTranslateY(other.getTranslateY() + ny * overlap / 2);
                    }
                }

                group.setTranslateX(x);
                group.setTranslateY(y);

                if (System.currentTimeMillis() - startTime >= 3000) {
                    stop();
                    animateToTarget(group, lw);
                }
            }
        };

        timer.start();

        group.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                highlight(lw);
                if (onLabWorkSelected != null) onLabWorkSelected.accept(lw);
            }
        });
    }

    private void animateToTarget(Group group, LabWorkWithKey lw) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(800), group);
        tt.setToX(normalizeX(lw.getCoordinates().getX()));
        tt.setToY(normalizeY(lw.getCoordinates().getY()));
        tt.play();
    }

    private void redrawAll() {
        getChildren().clear();
        visualized.clear();
        for (LabWorkWithKey lw : labWorks) addWithAnimation(lw);
        if (selectedLabWork != null) highlight(selectedLabWork);
    }

    private double normalizeX(double x) {
        return (x % 1000) / 1000 * (getWidth() - 40);
    }

    private double normalizeY(double y) {
        return (y % 1000) / 1000 * (getHeight() - 40);
    }

    private Color getColorForOwner(Long ownerId) {
        return ownerColorMap.computeIfAbsent(ownerId, id ->
                Color.hsb(rand.nextDouble() * 360, 0.7, 0.9));
    }

    private String buildTooltipText(LabWork lw) {
        StringBuilder sb = new StringBuilder();

        sb.append("ID: ").append(lw.getId()).append("\n");
        sb.append(lm.get("label.name")).append(": ").append(lw.getName()).append("\n");

        sb.append(lm.get("title.coordinates")).append(": ")
                .append(lm.get("label.x")).append(" = ").append(numberFormat.format(lw.getCoordinates().getX()))
                .append(", ")
                .append(lm.get("label.y")).append(" = ").append(numberFormat.format(lw.getCoordinates().getY()))
                .append("\n");

        sb.append(lm.get("label.creation_date")).append(": ")
                .append(lw.getCreationDate().toLocalDate().format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)))
                .append("\n");

        sb.append(lm.get("label.minimal_point")).append(": ").append(numberFormat.format(lw.getMinimalPoint())).append("\n");

        sb.append(lm.get("label.description")).append(": ").append(lw.getDescription()).append("\n");

        sb.append(lm.get("label.difficulty")).append(": ").append(lw.getDifficulty()).append("\n");

        if (lw.getDiscipline() != null) {
            sb.append(lm.get("title.discipline")).append(": ")
                    .append(lm.get("label.discipline_name")).append(" = ").append(lw.getDiscipline().getName())
                    .append(", ")
                    .append(lm.get("label.practice_hours")).append(" = ").append(integerFormat.format(lw.getDiscipline().getPracticeHours()))
                    .append("\n");
        }

        return sb.toString();
    }
}
