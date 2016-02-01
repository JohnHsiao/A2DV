import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class Main extends Application {	
	
	public static void main(String[] args) {
		launch(args);
	}
	
	public void start(Stage primaryStage) {
		
		FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Main.fxml"));		
		
		try {
			Parent root = (Parent)fxmlLoader.load();
			Scene scene = new Scene(root);
			scene.getStylesheets().add(getClass().getResource("css/Main.css").toExternalForm());
			primaryStage.setScene(scene);
			//primaryStage.setFullScreen(true);
			primaryStage.show();
		} catch(Exception e) {
			e.printStackTrace();
		}		
	}
	
	public void stop(){
		System.exit(0);
	}	
}