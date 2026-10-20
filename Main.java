import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import netscape.javascript.JSObject;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class Main extends Application {
    private static final List<String> TYPES = List.of("bowl", "house", "rium","coffee");

    // ===== 식물 시트(plant.png) 설정 =====
    private static final int SHEET_COLS = 3;          // plant.png 가로 칸 수
    private static final int SHEET_ROWS = 3;          // plant.png 세로 칸 수
    private static final double PLANT_SCALE = 1.2;    // 식물 크기 배율 (크게 하고 싶으면 숫자 키우기)

    private double xOffset = 0;
    private double yOffset = 0;

    private final Preferences prefs = Preferences.userRoot().node("terrarium");
    private final Bridge bridge = new Bridge();

    private Stage widgetStage;
    private Stage selectStage;
    private Stage settingStage;
    private StackPane root;
    private ImageView view;
    private boolean widgetShown = false;
    private double scale = 1.0;

    // ===== 식물 =====
    private Image plantSheet;
    private final List<Rectangle2D> plantRects = new ArrayList<>(); // 각 식물의 잘라낼 영역
    private Pane plantLayer;
    private int selectedPlant = -1;   // -1 = 심기 모드 아님

    private record Planted(int index, double rx, double ry) {}  // 식물 번호, 위치(0~1 비율)
    private final List<Planted> planted = new ArrayList<>();

    public class Bridge {
        public void select(String type) {
            System.out.println("선택: " + type);
            if (!TYPES.contains(type)) return;

            prefs.put("type", type);

            if (widgetShown) {
                view.setImage(loadImage(type));
                applyScale(scale);
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
        if (hour >= 6 && hour < 9)  return "rgb(202, 240, 255)"; // 아침
        if (hour >= 9 && hour < 12)  return "rgb(124, 187, 249)"; // 아침
        if (hour >= 12 && hour < 15) return "rgb(9, 174, 251)"; // 낮
        if (hour >= 15 && hour < 18) return "rgba(255, 200, 100, 1)"; // 낮
        if (hour >= 18 && hour < 21) return "rgba(255, 120, 80, 1)";  // 저녁
        return "rgba(20, 20, 60, 1)";                                  // 밤
    }

    private static Image loadImage(String type) {
        return new Image("file:images/" + type + ".png");
    }

    @Override
    public void start(Stage primaryStage) {
        widgetStage = primaryStage;
        loadPlantSheet();

        String choice = prefs.get("type", null);
        if (choice != null && TYPES.contains(choice)) {
            showWidget(choice);
        } else {
            showSelector();
        }
    }

    private void loadPlantSheet() {
        plantSheet = new Image("file:images/plant.png");
        plantRects.clear();
        if (plantSheet.isError()) {
            System.out.println("images/plant.png 를 불러올 수 없습니다.");
            return;
        }

        PixelReader pr = plantSheet.getPixelReader();
        int cellW = (int) plantSheet.getWidth() / SHEET_COLS;
        int cellH = (int) plantSheet.getHeight() / SHEET_ROWS;

        for (int row = 0; row < SHEET_ROWS; row++) {
            for (int col = 0; col < SHEET_COLS; col++) {
                int x0 = col * cellW, y0 = row * cellH;
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;

                for (int y = y0; y < y0 + cellH; y++) {
                    for (int x = x0; x < x0 + cellW; x++) {
                        int alpha = (pr.getArgb(x, y) >>> 24);
                        if (alpha > 10) {
                            if (x < minX) minX = x;
                            if (x > maxX) maxX = x;
                            if (y < minY) minY = y;
                            if (y > maxY) maxY = y;
                        }
                    }
                }
                if (maxX >= 0) { // 그림이 있는 칸만
                    plantRects.add(new Rectangle2D(minX, minY, maxX - minX + 1, maxY - minY + 1));
                }
            }
        }
        System.out.println("식물 " + plantRects.size() + "개 발견");
    }

    private ImageView makePlant(int index) {
        ImageView iv = new ImageView(plantSheet);  // 같은 Image를 재사용
        iv.setViewport(plantRects.get(index));     // 그 중 이 영역만 보여줌
        iv.setPreserveRatio(true);
        return iv;
    }

    // ===== 크기 적용 =====
    private void applyScale(double s) {
        scale = s;
        if (view == null) return;
        view.setPreserveRatio(true);
        view.setFitWidth(view.getImage().getWidth() * s);
        redrawPlants();
        widgetStage.sizeToScene();
    }

    private double viewW() { return view.getFitWidth(); }
    private double viewH() { return view.getBoundsInLocal().getHeight(); }

    // ===== 식물 다시 그리기 =====
    private void redrawPlants() {
        if (plantLayer == null) return;
        plantLayer.getChildren().clear();

        double k = scale * 0.3 * PLANT_SCALE;

        for (Planted p : planted) {
            if (p.index() >= plantRects.size()) continue;
            Rectangle2D r = plantRects.get(p.index());
            ImageView iv = makePlant(p.index());
            double w = r.getWidth() * k;
            double h = r.getHeight() * k;
            iv.setFitWidth(w);
            iv.setLayoutX(p.rx() * viewW() - w / 2);
            iv.setLayoutY(p.ry() * viewH() - h);

            // 식물 우클릭 → 뽑기
            iv.setOnContextMenuRequested(e -> {
                planted.remove(p);
                savePlants();
                redrawPlants();
                e.consume();
            });
            plantLayer.getChildren().add(iv);
        }
    }

    private void startPlanting(int index) {
        selectedPlant = index;
        root.setCursor(Cursor.CROSSHAIR);
    }

    private void stopPlanting() {
        selectedPlant = -1;
        root.setCursor(Cursor.DEFAULT);
    }

    // ===== 저장 / 불러오기 =====
    private void savePlants() {
        StringBuilder sb = new StringBuilder();
        for (Planted p : planted) {
            sb.append(p.index()).append(',').append(p.rx()).append(',').append(p.ry()).append(';');
        }
        prefs.put("plants", sb.toString());
    }

    private void loadPlants() {
        planted.clear();
        for (String part : prefs.get("plants", "").split(";")) {
            if (part.isBlank()) continue;
            try {
                String[] a = part.split(",");
                planted.add(new Planted(Integer.parseInt(a[0]),
                        Double.parseDouble(a[1]), Double.parseDouble(a[2])));
            } catch (Exception ignore) { }
        }
    }

    // ===== 설정창 =====
    private void setting() {
        if (settingStage != null && settingStage.isShowing()) {
            settingStage.toFront();
            return;
        }

        Label opacityLabel = new Label();
        Slider opacitySlider = new Slider(0.2, 1.0, widgetStage.getOpacity());
        opacityLabel.setText("투명도: " + Math.round(opacitySlider.getValue() * 100) + "%");

        Label sizeLabel = new Label();
        Slider sizeSlider = new Slider(0.3, 1.5, scale);
        sizeLabel.setText("크기: " + Math.round(sizeSlider.getValue() * 100) + "%");

        opacitySlider.valueProperty().addListener((obs, oldV, newV) -> {
            double v = newV.doubleValue();
            widgetStage.setOpacity(v);
            opacityLabel.setText("투명도: " + Math.round(v * 100) + "%");
            prefs.putDouble("opacity", v);
        });

        sizeSlider.valueProperty().addListener((obs, oldV, newV) -> {
            double v = newV.doubleValue();
            applyScale(v);
            sizeLabel.setText("크기: " + Math.round(v * 100) + "%");
            prefs.putDouble("scale", v);
        });

        Button resetBtn = new Button("기본값으로");
        resetBtn.setOnAction(e -> {
            opacitySlider.setValue(1.0);
            sizeSlider.setValue(1.0);
        });

        VBox box = new VBox(10, opacityLabel, opacitySlider, sizeLabel, sizeSlider, resetBtn);
        box.setPadding(new Insets(16));

        settingStage = new Stage();
        settingStage.setTitle("설정");
        settingStage.setScene(new Scene(box, 280, 200));
        settingStage.setAlwaysOnTop(true);
        settingStage.show();
    }

    // ===== 테라리움 선택창 =====
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
        root = new StackPane();
        root.setStyle(rootStyle());

        view = new ImageView(loadImage(choice));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        Label timeLabel = new Label(LocalTime.now().format(fmt));
        timeLabel.setTextFill(Color.WHITE);
        timeLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");
        StackPane.setAlignment(timeLabel, Pos.TOP_CENTER);
        StackPane.setMargin(timeLabel, new Insets(12));

        // 식물 레이어: 그림 위에 겹쳐지는 투명한 판
        plantLayer = new Pane();
        plantLayer.setPrefSize(0, 0);   // 식물 때문에 창이 커지지 않게
        Rectangle clip = new Rectangle();   // 둥근 모서리 밖으로 식물이 삐져나가지 않게
        clip.widthProperty().bind(plantLayer.widthProperty());
        clip.heightProperty().bind(plantLayer.heightProperty());
        clip.setArcWidth(40);
        clip.setArcHeight(40);
        plantLayer.setClip(clip);

        // 심기 모드에서 클릭하면 그 자리에 심기
        plantLayer.setOnMousePressed(e -> {
            if (selectedPlant < 0 || e.getButton() != MouseButton.PRIMARY) return; // 아니면 창 드래그로 넘김
            planted.add(new Planted(selectedPlant, e.getX() / viewW(), e.getY() / viewH()));
            savePlants();
            redrawPlants();
            if (!e.isShiftDown()) stopPlanting();   // Shift 누른 채로 클릭하면 연속 심기
            e.consume();   // 창 드래그 핸들러로 이벤트가 안 올라가게
        });

        root.getChildren().addAll(view, timeLabel, plantLayer);

        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            timeLabel.setText(LocalTime.now().format(fmt));
            root.setStyle(rootStyle());
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();

        // ===== 우클릭 메뉴 =====
        MenuItem settingItem = new MenuItem("설정");
        settingItem.setOnAction(e -> setting());

        MenuItem changeItem = new MenuItem("테라리움 바꾸기");
        changeItem.setOnAction(e -> showSelector());

        Menu plantMenu = new Menu("식물 심기");
        for (int i = 0; i < plantRects.size(); i++) {
            int idx = i;
            ImageView icon = makePlant(i);
            icon.setFitWidth(32);
            MenuItem item = new MenuItem("식물 " + (i + 1), icon);
            item.setOnAction(e -> startPlanting(idx));
            plantMenu.getItems().add(item);
        }

        MenuItem clearItem = new MenuItem("식물 모두 뽑기");
        clearItem.setOnAction(e -> {
            planted.clear();
            savePlants();
            redrawPlants();
        });

        MenuItem exitItem = new MenuItem("종료");
        exitItem.setOnAction(e -> Platform.exit());

        ContextMenu menu = new ContextMenu(settingItem, changeItem, new SeparatorMenuItem(),
                plantMenu, clearItem, new SeparatorMenuItem(), exitItem);
        root.setOnContextMenuRequested(e -> {
            stopPlanting();
            menu.show(root, e.getScreenX(), e.getScreenY());
        });

        // ===== 창 드래그 =====
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
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stopPlanting();   // ESC = 심기 취소
        });

        // 저장된 값 불러오기
        loadPlants();
        widgetStage.setOpacity(prefs.getDouble("opacity", 1.0));
        applyScale(prefs.getDouble("scale", 1.0));

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
