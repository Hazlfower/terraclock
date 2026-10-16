
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.time.LocalTime;



public class Main extends Application {
    private double xOffset = 0;
    private double yOffset = 0;


    public static int nowHour() {
        return LocalTime.now().getHour();   // 0 ~ 23
    }

    public static String bgColor(int hour) {
    if (hour >= 6 && hour < 12)  return "rgba(135, 206, 235, 1)"; // 아침
    if (hour >= 12 && hour < 18) return "rgba(255, 200, 100, 1)"; // 낮
    if (hour >= 18 && hour < 21) return "rgba(255, 120, 80, 1)";  // 저녁
    return "rgba(20, 20, 60, 1)";                                  // 밤
    }


    @Override
    public void start(Stage primaryStage) {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: " + bgColor(nowHour()) + "; -fx-background-radius: 20px;");



        Label label = new Label("테라리움");
        label.setTextFill(Color.WHITE);
        root.getChildren().add(label);
        
        Image img = new Image("file:images/house.png");
        ImageView view = new ImageView(img);
        root.getChildren().add(view);


        root.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        

        root.setOnMouseDragged(event -> {
            primaryStage.setX(event.getScreenX() - xOffset);
            primaryStage.setY(event.getScreenY() - yOffset);
        });
        

        

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setAlwaysOnTop(true);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    
    public static void main(String[] args) {
        launch(args);
    }
}