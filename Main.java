import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import netscape.javascript.JSObject;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.prefs.Preferences;

public class Main extends Application {
    private static final List<String> TYPES = List.of("bowl", "house", "rium");

    private double xOffset = 0;
    private double yOffset = 0;

    private final Preferences prefs = Preferences.userRoot().node("terrarium");

    private final Bridge bridge = new Bridge();

    private Stage widgetStage;
    private Stage selectStage;
    private ImageView view;
    private boolean widgetShown = false;

    public class Bridge {
        public void select(String type) {
            System.out.println("선택: " + type);
            if (!TYPES.contains(type)) return;

            prefs.put("type", type);

            if (widgetShown) {
                view.setImage(loadImage(type));
            } else {
                showWidget(type);
            }

            if (selectStage != null) selectStage.close();
        }
    }

    public static int nowHour() {
        return LocalTime.now().getHour();   // 0 ~ 23
    }

    public static String bgColor(int hour) {
        if (hour >= 6 && hour < 12)  return "rgba(135, 206, 235, 1)"; // 아침
        if (hour >= 12 && hour < 18) return "rgba(255, 200, 100, 1)"; // 낮
        if (hour >= 18 && hour < 21) return "rgba(255, 120, 80, 1)";  // 저녁
        return "rgba(20, 20, 60, 1)";                                  // 밤
    }

    private static Image loadImage(String type) {
        return new Image("file:images/" + type + ".png");
    }

    @Override
    public void start(Stage primaryStage) {
        widgetStage = primaryStage;

        String choice = prefs.get("type", null);
        if (choice != null && TYPES.contains(choice)) {
            showWidget(choice);
        } else {
            showSelector();
        }
    }

    private void showSelector() {
        if (selectStage != null && selectStage.isShowing()) {
            selectStage.toFront();
            return;
        }

        File html = new File("select.html");
        if (!html.exists()) {
            System.out.println("select.html 파일을 찾을 수 없습니다: " + html.getAbsolutePath());
            Platform.exit();
            return;
        }

        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("app", bridge);
            }
        });
        engine.load(html.toURI().toString());

        selectStage = new Stage();
        selectStage.setTitle("테라리움 선택");
        selectStage.setScene(new Scene(webView, 820, 560));
        selectStage.setAlwaysOnTop(true);
        selectStage.show();
    }

    // ===== 투명 위젯 =====
    private void showWidget(String choice) {
        StackPane root = new StackPane();
        root.setStyle(rootStyle());

        view = new ImageView(loadImage(choice));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        Label timeLabel = new Label(LocalTime.now().format(fmt));
        timeLabel.setTextFill(Color.WHITE);
        timeLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");

        root.getChildren().add(view);
        root.getChildren().add(timeLabel);
        StackPane.setAlignment(timeLabel, Pos.TOP_CENTER);
        StackPane.setMargin(timeLabel, new Insets(12));

        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            timeLabel.setText(LocalTime.now().format(fmt));
            root.setStyle(rootStyle());
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();

        MenuItem changeItem = new MenuItem("테라리움 바꾸기");
        changeItem.setOnAction(e -> showSelector());

        MenuItem exitItem = new MenuItem("종료");
        exitItem.setOnAction(e -> Platform.exit());

        ContextMenu menu = new ContextMenu(changeItem, new SeparatorMenuItem(), exitItem);
        root.setOnContextMenuRequested(e -> menu.show(root, e.getScreenX(), e.getScreenY()));

        root.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                menu.hide();
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });

        root.setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) {
                widgetStage.setX(event.getScreenX() - xOffset);
                widgetStage.setY(event.getScreenY() - yOffset);
            }
        });

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        widgetStage.initStyle(StageStyle.TRANSPARENT);
        widgetStage.setAlwaysOnTop(true);
        widgetStage.setScene(scene);
        widgetStage.show();
        widgetShown = true;
    }

    private static String rootStyle() {
        return "-fx-background-color: " + bgColor(nowHour()) + "; -fx-background-radius: 20px;";
    }

    public static void main(String[] args) {
        launch(args);
    }
}