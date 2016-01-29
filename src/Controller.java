import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.magiclen.dialog.Dialog;
import org.magiclen.dialog.Dialog.DialogButtonEvent;
import org.magiclen.dialog.Dialogs;

import com.pi4j.gpio.extension.base.AdcGpioProvider;
import com.pi4j.gpio.extension.mcp.MCP3008GpioProvider;
import com.pi4j.gpio.extension.mcp.MCP3008Pin;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinAnalogInput;
import com.pi4j.io.spi.SpiChannel;

import dao.Dao;
import io.Writer;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
//import mcp.MCP3008;
import javafx.scene.text.FontWeight;

public class Controller implements Initializable {
	
	boolean debug=false; //開發階段
	
	int interval=1000;//預設間隔時間	

	Dao dao=new Dao();

	public AreaChart sc;	
	public AreaChart sc_h;
	public AreaChart sc_d;
	public AreaChart sc_w;
	public AreaChart sc_m;
	
	@FXML
	private TabPane tab;

	@FXML
	public Label val;
	
	@FXML
	public Label valx;
	
	@FXML
	public Label yearValue;
	
	@FXML
	public Label dayValue;
	
	@FXML
	public Label timeValue;
	
	private int year, month, day, hh, mm, ss;	
	
	public ExecutorService executor;

	private AddToQueue addToQueue;	
	
	private Calendar c=Calendar.getInstance();

	int num; //加權值
	
	int n; //取值
	
	GpioController gpio;
	AdcGpioProvider provider;
	GpioPinAnalogInput input;
	
	private void saveData() throws SQLException, IOException {
		dao.exSql("INSERT INTO adlog(val,original)VALUES("+num+","+n+");");
	}
	
	//更新時間
	private void setTime(){
		c.setTime(new Date());
		year=c.get(Calendar.YEAR);
		month=c.get(Calendar.MONTH)+1;
		day=c.get(Calendar.DAY_OF_MONTH);
		hh=c.get(Calendar.HOUR_OF_DAY);
		mm=c.get(Calendar.MINUTE);
		ss=c.get(Calendar.SECOND);
	}	
	
	int cs, cm, ch, cd;
	int s, smin, smax, m, min, max, h, hmin, hmax, d, dmin, dmax, w, wmin, wmax;	
	
	//計數器
	private void count(){		
		cs++;
		dataQ.add(num);
		if(s==0){//秒計為0時-取每分鐘值
			smin=num;
			smax=num;
		}else{
			if(num>smax)smax=num;
			if(num<smin)smin=num;
		}
		
		if(m==0){//分計為0時-取每小時值
			min=num;
			max=num;
		}else{
			if(num>max)max=num;
			if(num<min)min=num;
		}
		
		if(h==0){//分計為0時-取每小時值
			hmin=num;
			hmax=num;
		}else{
			if(num>hmax)hmax=num;
			if(num<hmin)hmin=num;
		}
		if(d==0){//分計為0時-取每小時值
			dmin=num;
			dmax=num;
		}else{
			if(num>dmax)dmax=num;
			if(num<dmin)dmin=num;
		}
		if(h==0){//分計為0時-取每小時值
			wmin=num;
			wmax=num;
		}else{
			if(num>wmax)wmax=num;
			if(num<wmin)wmin=num;
		}
		//smin=(num<smin)?num:smin;
		//smax=(num>smax)?num:smax;		
		s+=num;
		if(cs==60){
			m+=(s/60);//每分鐘平均值
			dataH.add(s/60);			
			dataHMini.add(smin);
			dataHMax.add(smax);
			cs=0;			
			s=0;
			cm++;
		}
		
		if(cm==60){
			h+=(m/60);//每小時平均值
			dataD.add(m/60);
			dataDMini.add(min);
			dataDMax.add(max);			
			cm=0;
			m=0;
			ch++;
		}
		
		if(ch==24){
			d+=(h/24);//每天平均
			dataW.add(h/24);
			dataWMini.add(hmin);
			dataWMax.add(hmax);			
			ch=0;
			h=0;
			cd++;
		}
		
		if(cd==7){
			dataM.add(d/7);//每週平均
			dataMMini.add(dmin);
			dataMMax.add(dmax);			
			cd=0;
			d=0;
			//cw++;
		}
	}	
	
	//加權值
	private int pow(int n){
		//235
		double step=0.0043478260869565d;
		if(n<235){
			return 0;
		}		
		//205~409
		if(n>=230 && n<460){	
			return (int)Math.pow(10, 1+((n-230)*step));
		}
		//411~614
		if(n>=460 && n<690){
			return (int)Math.pow(10, 2+((n-460)*step));
		}
		//615~819
		if(n>=690 && n<920){
			return (int)Math.pow(10, 3+((n-690)*step));
		}
		//820~1025
		if(n>=920){
			return (int)Math.pow(10, 4+((n-920)*step));
		}		
		return 0;
	}
	
	//資料排入駐列
	private class AddToQueue implements Runnable {

		public void run() {
			//開發期間以亂數
			if(debug){
				n=(int) (Math.random() * (500 - 460 + 1)) + 460;
				num = pow(n);	         				
			}else{
				n=(int) input.getValue();	
				num=pow(n);
			}			
			//每次間隔時間執行工作
			count();
			setTime();//更新時間
			try {
				saveData();//儲存資料				
				Thread.sleep(interval);				
			} catch (Exception e) {
				e.printStackTrace();
			}
			executor.execute(this);
			
			// 同步更新需起另1個執行緒
			Platform.runLater(new Runnable() {
				@Override
				public void run() {
					valx.setText(n + "");	
					val.setText(num + "");
					yearValue.setText(String.valueOf(year)+"/");
					dayValue.setText(String.valueOf( month+"/"+day ));
					timeValue.setText(String.valueOf(hh+":"+mm+"."+ss));
					
					//更新圖表
					addDataToSeries();
					if(cs==0){					
						addDataToHourSeries();
					}
					if(cm==0){
						addDataToDaySeries();
					}
					if(ch==0){
						addDataToWeekSeries();
					}
					if(cd==0){
						addDataToMonthSeries();
					}
				}
			});
		}
	}
	
	//每分鐘圖表
	@FXML
	private NumberAxis xAxis;
	@FXML
	private NumberAxis yAxis;
	
	private ConcurrentLinkedQueue<Number> dataQ = new ConcurrentLinkedQueue<Number>();	
	private Series series;
	private int seriesData = 0;
	
	private void addDataToSeries() {		
		for (int i = 0; i < 60; i++) {
			if (dataQ.isEmpty()){
				break;
			}			
			series.getData().add(new AreaChart.Data(seriesData++, dataQ.remove()));
		}
		if (series.getData().size() > 60) {
			series.getData().remove(0, (series.getData().size() - 60));
		}
		xAxis.setLowerBound(seriesData - 60);
		xAxis.setUpperBound(seriesData - 1);		
	}
	
	//每小時圖表
	@FXML
	private NumberAxis xAxis_h;
	@FXML
	private NumberAxis yAxis_h;
	
	private Series series_h;
	private Series series_h_min;
	private Series series_h_max;
	
	private int seriesData_h = 0;
	
	private ConcurrentLinkedQueue<Number> dataH = new ConcurrentLinkedQueue<Number>();	
	private ConcurrentLinkedQueue<Number> dataHMini = new ConcurrentLinkedQueue<Number>();
	private ConcurrentLinkedQueue<Number> dataHMax = new ConcurrentLinkedQueue<Number>();
	
	private void addDataToHourSeries() {		
		
		for (int i = 0; i < 61; i++) {
			if (dataH.isEmpty()){
				break;
			}		
			
			series_h.getData().add(new AreaChart.Data(seriesData_h++, dataH.remove()));				
			series_h_min.getData().add(new AreaChart.Data(seriesData_h-1, dataHMini.remove()));			
			series_h_max.getData().add(new AreaChart.Data(seriesData_h-1, dataHMax.remove()));
			
			
		}
		if (series_h.getData().size() > 61) {
			series_h.getData().remove(0, (series_h.getData().size() - 61));
			series_h_min.getData().remove(0, (series_h_min.getData().size() - 61));
			series_h_max.getData().remove(0, (series_h_max.getData().size() - 61));
		}
		xAxis_h.setLowerBound(seriesData_h - 60);
		xAxis_h.setUpperBound(seriesData_h - 1);
	}
	
	//每日圖表
	@FXML
	private NumberAxis xAxis_d;
	@FXML
	private NumberAxis yAxis_d;
	
	private Series series_d;
	private Series series_d_min;
	private Series series_d_max;
	
	private int seriesData_d = 0;
	
	private ConcurrentLinkedQueue<Number> dataD = new ConcurrentLinkedQueue<Number>();	
	private ConcurrentLinkedQueue<Number> dataDMini = new ConcurrentLinkedQueue<Number>();
	private ConcurrentLinkedQueue<Number> dataDMax = new ConcurrentLinkedQueue<Number>();
	private void addDataToDaySeries() {
		for (int i = 0; i < 25; i++) {
			if (dataD.isEmpty()){
				break;
			}	
			series_d.getData().add(new AreaChart.Data(seriesData_d++, dataD.remove()));			
			series_d_min.getData().add(new AreaChart.Data(seriesData_d-1, dataDMini.remove()));
			series_d_max.getData().add(new AreaChart.Data(seriesData_d-1, dataDMax.remove()));
		}
		if (series_d.getData().size() > 25) {
			series_d.getData().remove(0, (series_d.getData().size() - 25));
			series_d_min.getData().remove(0, (series_d_min.getData().size() - 25));
			series_d_max.getData().remove(0, (series_d_max.getData().size() - 25));
		}
		xAxis_d.setLowerBound(seriesData_d - 24);
		xAxis_d.setUpperBound(seriesData_d - 1);
	}
	
	//每週圖表
	@FXML
	private NumberAxis xAxis_w;
	@FXML
	private NumberAxis yAxis_w;
	
	private Series series_w;
	private Series series_w_min;
	private Series series_w_max;
	
	private int seriesData_w = 0;
	
	private ConcurrentLinkedQueue<Number> dataW = new ConcurrentLinkedQueue<Number>();	
	private ConcurrentLinkedQueue<Number> dataWMini = new ConcurrentLinkedQueue<Number>();
	private ConcurrentLinkedQueue<Number> dataWMax = new ConcurrentLinkedQueue<Number>();
	private void addDataToWeekSeries() {
		
		for (int i = 0; i < 8; i++) {
			if (dataW.isEmpty()){
				break;
			}	
			series_w.getData().add(new AreaChart.Data(seriesData_w++, dataW.remove()));			
			series_w_min.getData().add(new AreaChart.Data(seriesData_w-1, dataWMini.remove()));
			series_w_max.getData().add(new AreaChart.Data(seriesData_w-1, dataWMax.remove()));
		}
		
		if (series_w.getData().size() > 8) {
			series_w.getData().remove(0, (series_w.getData().size() - 8));
			series_w_min.getData().remove(0, (series_w_min.getData().size() - 8));
			series_w_max.getData().remove(0, (series_w_max.getData().size() - 8));
		}
		xAxis_w.setLowerBound(seriesData_w - 7);
		xAxis_w.setUpperBound(seriesData_w - 1);
	}
	
	//每月圖表
	@FXML
	private NumberAxis xAxis_m;
	@FXML
	private NumberAxis yAxis_m;
	
	private Series series_m;
	private Series series_m_min;
	private Series series_m_max;
	
	private int seriesData_m = 0;
	
	private ConcurrentLinkedQueue<Number> dataM = new ConcurrentLinkedQueue<Number>();	
	private ConcurrentLinkedQueue<Number> dataMMini = new ConcurrentLinkedQueue<Number>();
	private ConcurrentLinkedQueue<Number> dataMMax = new ConcurrentLinkedQueue<Number>();
	private void addDataToMonthSeries() {
		for (int i = 0; i < 5; i++) {
			if (dataM.isEmpty()){
				break;
			}	
			series_m.getData().add(new AreaChart.Data(seriesData_m++, dataM.remove()));			
			series_m_min.getData().add(new AreaChart.Data(seriesData_m-1, dataMMini.remove()));
			series_m_max.getData().add(new AreaChart.Data(seriesData_m-1, dataMMax.remove()));
		}
		if (series_m.getData().size() > 5) {
			series_m.getData().remove(0, (series_m.getData().size() - 5));
			series_m_min.getData().remove(0, (series_m_min.getData().size() - 5));
			series_m_max.getData().remove(0, (series_m_max.getData().size() - 5));
		}
		xAxis_m.setLowerBound(seriesData_m - 4);
		xAxis_m.setUpperBound(seriesData_m - 1);
	}
	
	
	public void initialize(URL url, ResourceBundle rb) {
		//Font f=Font.loadFont(Main.class.getResource("/fonts/elektra.ttf").toExternalForm(), );
		
		valx.setFont(Font.loadFont(Main.class.getResource("/fonts/elektra.ttf").toExternalForm(), 24));
		val.setFont(Font.loadFont(Main.class.getResource("/fonts/digital.ttf").toExternalForm(), 160));
		
		yearValue.setFont(Font.loadFont(Main.class.getResource("/fonts/elektra.ttf").toExternalForm(), 48));
		timeValue.setFont(Font.loadFont(Main.class.getResource("/fonts/elektra.ttf").toExternalForm(), 48));
		dayValue.setFont(Font.loadFont(Main.class.getResource("/fonts/elektra.ttf").toExternalForm(), 48));
		
		
		if(debug){
			interval=100;
		}else{
			try{
				gpio = GpioFactory.getInstance();
				runGPIO();
			}catch(Exception e){
				
			}
		}        
		//min
		series = new AreaChart.Series<Number, Number>();
		//series.setName("Value");
		xAxis.setForceZeroInRange(false);
		xAxis.setAutoRanging(false);
		xAxis.setTickLabelsVisible(false);
		sc.setAnimated(false);
		sc.getData().add(series);
		
		//hour
		series_h = new AreaChart.Series<Number, Number>();
		series_h_min= new AreaChart.Series<Number, Number>();
		series_h_max= new AreaChart.Series<Number, Number>();		
		//series_h.setName("Avg Value");
		xAxis_h.setForceZeroInRange(false);
		xAxis_h.setAutoRanging(false);
		xAxis_h.setTickLabelsVisible(false);
		sc_h.setAnimated(false);
		sc_h.getData().addAll(series_h_max, series_h, series_h_min);
		
		//day		
		series_d = new AreaChart.Series<Number, Number>();
		series_d_min= new AreaChart.Series<Number, Number>();
		series_d_max= new AreaChart.Series<Number, Number>();
		xAxis_d.setForceZeroInRange(false);
		xAxis_d.setAutoRanging(false);
		xAxis_d.setTickLabelsVisible(false);
		sc_d.setAnimated(false);
		sc_d.getData().addAll(series_d_max, series_d, series_d_min);
		
		//week
		series_w = new AreaChart.Series<Number, Number>();
		series_w_min= new AreaChart.Series<Number, Number>();
		series_w_max= new AreaChart.Series<Number, Number>();
		xAxis_w.setForceZeroInRange(false);
		xAxis_w.setAutoRanging(false);
		xAxis_w.setTickLabelsVisible(false);
		sc_w.setAnimated(false);
		sc_w.getData().addAll(series_w_max, series_w, series_w_min);
		
		//month
		series_m = new AreaChart.Series<Number, Number>();
		series_m_min= new AreaChart.Series<Number, Number>();
		series_m_max= new AreaChart.Series<Number, Number>();
		xAxis_m.setForceZeroInRange(false);
		xAxis_m.setAutoRanging(false);
		xAxis_m.setTickLabelsVisible(false);
		sc_m.setAnimated(false);
		sc_m.getData().addAll(series_m_max, series_m, series_m_min);
		
		runChart();
		
		
	}

	private void runChart() {
		executor = Executors.newCachedThreadPool();
		addToQueue = new AddToQueue();
		executor.execute(addToQueue);
	}
	
	private void runGPIO() throws IOException{		
		provider = new MCP3008GpioProvider(SpiChannel.CS0);		
        input = gpio.provisionAnalogInputPin(provider, MCP3008Pin.CH0, "CH0");
        //provider.setEventThreshold(100, input); 
        //provider.setMonitorInterval(500); // milliseconds	
	}
	
	/*private void exit(ActionEvent event) {
		Alert alert = new Alert(AlertType.CONFIRMATION); // 實體化Alert對話框物件，並直接在建構子設定對話框的訊息類型
		alert.setTitle("關閉程式"); //設定對話框視窗的標題列文字
		alert.setHeaderText(""); //設定對話框視窗裡的標頭文字。若設為空字串，則表示無標頭
		alert.setContentText("是否立即結束程式？"); //設定對話框的訊息文字
		Optional<ButtonType> opt = alert.showAndWait();
		final ButtonType rtn = opt.get(); //可以直接用「alert.getResult()」來取代
		System.out.println(rtn);
		if (rtn == ButtonType.OK) {		    
		    Platform.exit(); // 結束程式
		} else if(rtn == ButtonType.CANCEL){		    
		    Alert alert2 = new Alert(AlertType.INFORMATION); // 實體化Alert對話框物件，並直接在建構子設定對話框的訊息類型
		    alert2.setTitle("小提示"); //設定對話框視窗的標題列文字
		    alert2.setHeaderText("現在該做什麼？"); //設定對話框視窗裡的標頭文字。若設為空字串，則表示無標頭
		    alert2.setContentText("請按下「確定」按鈕。"); //設定對話框的訊息文字
		    alert2.showAndWait(); //顯示對話框，並等待對話框被關閉時才繼續執行之後的程式
		}
		if(!debug){
			gpio.shutdown();
		}
		
		System.exit(0);
	}*/
	
	
	private String getPath(){		
		if(debug){
			return "C:/dev/log/";
		}else{			
			File file=new File("/media/pi/");			
			return "/media/pi/"+file.list()[0]+"/";
		}		
	}
	
	
	private void write(List<Map>list, String fileName) throws SQLException{		
		Writer w=new Writer();
		String path=getPath()+fileName;
		w.deleteFile(path);
		w.createNewFile(path);	
		
		StringBuilder sb=new StringBuilder();
		if(list.size()>5000){
			for(int i=0; i<list.size(); i++){
				sb.append(list.get(i).get("time")+","+list.get(i).get("val")+","+list.get(i).get("original")+"\n");
				if(i%5000==0){
					w.writeText(sb.toString(),path,"utf8",true);
					sb=new StringBuilder();
				}
			}
			w.writeText(sb.toString(),path,"utf8",true);
		}else{
			for(int i=0; i<list.size(); i++){
				sb.append(list.get(i).get("time")+","+list.get(i).get("val")+","+list.get(i).get("original")+"\n");
			}
			w.writeText(sb.toString(),path,"utf8",true);
		}		
	}
	
	@FXML
	private void close(ActionEvent event) {
		if(!debug){
			gpio.shutdown();
		}
		System.exit(0);
	}
	
	//關閉下載視窗
	private static final DialogButtonEvent Ev=new closeBtn();
	private static class closeBtn implements DialogButtonEvent{

		public void onClick() {
			
		}
	}
	
	private String getRange(int type){
		SimpleDateFormat sf=new SimpleDateFormat("yyyy-MM-dd");
		Calendar c=Calendar.getInstance();
		//day
		if(type==0){
			c.add(Calendar.DAY_OF_YEAR, -1);
		}
		//week
		if(type==1){
			c.add(Calendar.WEEK_OF_YEAR, -1);
		}
		//month
		if(type==2){
			c.add(Calendar.MONTH, -1);
		}
		//year
		if(type==3){
			c.add(Calendar.YEAR, -1);
		}
		return sf.format(c.getTime());
	}	
	
	@FXML
	private void save24(ActionEvent event) throws SQLException {		
		List list=dao.sqlGet("SELECT * FROM adlog WHERE time>'"+getRange(0)+"'");		
		write(list, "log_day");		
		Dialogs d=Dialogs.create();
		d.message("Success!");
		d.addButton("Close", Ev);			
		d.show();		
	}
	
	@FXML
	private void saveWeek(ActionEvent event) throws SQLException {		
		
		List list=dao.sqlGet("SELECT * FROM adlog WHERE time>'"+getRange(1)+"'");		
		write(list, "log_week.csv");		
		Dialogs d=Dialogs.create();
		d.message("Success!");
		d.addButton("Close", Ev);			
		d.show();	
	}
	
	@FXML
	private void saveMonth(ActionEvent event) throws SQLException {
		List list=dao.sqlGet("SELECT * FROM adlog WHERE time>'"+getRange(2)+"'");		
		write(list, "log_month.csv");		
		Dialogs d=Dialogs.create();
		d.message("Success!");
		d.addButton("Close", Ev);			
		d.show();
	}
	
	@FXML
	public DatePicker begin;
	
	@FXML
	public DatePicker end;
	
	@FXML
	private void saveCustom(ActionEvent event) throws SQLException {
		//SimpleDateFormat sf=new SimpleDateFormat("yyyy-MM-dd");
		if(begin==null){
			begin.setValue(LocalDate.now());
		}
		if(end==null){
			end.setValue(LocalDate.now());
		}
		List list=dao.sqlGet("SELECT * FROM adlog WHERE time>='"+begin.getValue()+"'AND time<='"+end.getValue()+"'");		
		write(list, "log_custom.csv");		
		Dialogs d=Dialogs.create();
		d.message("Success!");
		d.addButton("Close", Ev);			
		d.show();
	}
	
	@FXML
	private void saveYear(ActionEvent event) throws SQLException {
		List list=dao.sqlGet("SELECT * FROM adlog WHERE time>'"+getRange(3)+"'");		
		write(list, "log_year.csv");		
		Dialogs d=Dialogs.create();
		d.message("Success!");
		d.addButton("Close", Ev);			
		d.show();
	}

}
