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

import org.magiclen.dialog.Dialog.DialogButtonEvent;
import org.magiclen.dialog.Dialogs;

import com.pi4j.gpio.extension.base.AdcGpioProvider;
import com.pi4j.gpio.extension.mcp.MCP3008GpioProvider;
import com.pi4j.gpio.extension.mcp.MCP3008Pin;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinAnalogInput;
import com.pi4j.io.gpio.event.GpioPinAnalogValueChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerAnalog;
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
import javafx.scene.control.Slider;
import javafx.scene.control.TabPane;

public class Controller implements Initializable {
	
	boolean debug=true; //開發階段
	
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
	public Label timeValue;	
	public ExecutorService executor;
	private AddToQueue addToQueue;

	int num;	
	int n; //取值
	
	private GpioController gpio;
	private AdcGpioProvider provider;
	private GpioPinAnalogInput input;
	
	private void saveData() throws SQLException, IOException {
		dao.exSql("INSERT INTO adlog(val,original)VALUES("+num+","+n+");");
		vc=0;					
		nv=0;
    	mv=0;
	}
	
	final SimpleDateFormat sf=new SimpleDateFormat("yyyy-MM-dd");
	final SimpleDateFormat sf1=new SimpleDateFormat("HH:mm.ss");
	String day, time;
	Date now;
	
	//更新時間
	private void setTime(){
		now=new Date();
		day=sf.format(now);
		time=sf1.format(now);	
	}	
	
	int cs, cm, ch, cd;
	int s, smin, smax, m, min, max, h, hmin, hmax, d, dmin, dmax, w, wmin, wmax;	
	
	//計數器
	private void count(){		
		cs++;		
		dataQMini.add(pow(nv));
		dataQMax.add(pow(mv));
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
		}
	}	
	
	//加權值
	/*final double POWN1=1/1275d;
	final double POWN2=1/1024d;
	final double POWN3=1/1053d;
	final double POWN4=1/743d;*/
	final double POW=0.00390625;
	/*final double POWN1=1/275d;
	final double POWN2=1/273d;
	final double POWN3=1/260d;
	final double POWN4=1/219d;*/
	private int pow(int n){
		/*
		//0~1.25v 8.08
		if(n<=1309){			
			return (int)Math.pow(10, 1+(n*POWN1));
		}		
		//1.25~2.50
		if(n>1309 && n<=2412){	
			return (int)Math.pow(10, 2+((n-1275)*POWN2));
		}
		//2.50~3.75
		if(n>2412 && n<=3437){
			return (int)Math.pow(10, 3+((n-2299)*POWN3));
		}
		//3.75+ 
		if(n>3437){
			return (int)Math.pow(10, 4+((n-3352)*POWN4));
		}
		*/
		/*
		//0~1.25V, 8.056mA
		if(n<=274){			
			return (int)Math.pow(10, 1+(n*POWN1));
		}		
		//1.25~2.50, 12.055mA
		if(n>274 && n<=546){	
			return (int)Math.pow(10, 2+((n-274)*POWN2));
		}
		//2.50~3.75,  16.05mA
		if(n>546 && n<=805){
			return (int)Math.pow(10, 3+((n-546)*POWN3));
		}
		//3.75~4.87, 19.65mA
		if(n>805 && n<1023){
			return (int)Math.pow(10, 4+((n-805)*POWN4));
		}
		*/
		if(n>0 && n<=268){			
			return (int)Math.pow(10, 1+(n*POW));
		}		
		//1.25~2.50, 12.055mA
		if(n>268 && n<=524){	
			return (int)Math.pow(10, 2+((n-268)*POW));
		}
		//2.50~3.75,  16.05mA
		if(n>524 && n<=780){
			return (int)Math.pow(10, 3+((n-524)*POW));
		}
		//3.75~4.87, 19.65mA
		if(n>780 && n<=1023){
			return (int)Math.pow(10, 4+((n-780)*POW));
		}
		return 99999;
	}
	
	int mv;//取樣取大值
	int nv;//取樣最小值
    int vc;//取樣次數
    short r;
	//監聽器
	private GpioPinListenerAnalog listener = new GpioPinListenerAnalog(){            
        public void handleGpioPinAnalogValueChangeEvent(GpioPinAnalogValueChangeEvent event){
        	n=(int)event.getValue();
        	//if(r>0)n=r;
        	if(vc==0){
        		nv=n;
        		mv=n;
        	}else{
        		if(n>mv)mv=n;
        		if(n<nv)nv=n;
        	}
        	vc++;
        	//
        	Platform.runLater(new Runnable() {
				@Override
				public void run() {
					sps.setText(vc+"");
		            minValue.setText(nv+"");
		            maxValue.setText(mv+"");
				}
			});
        }
        
        
    };    
	
    @FXML
    public Label avgValue;
    @FXML
    public Label minValue;
    @FXML
    public Label maxValue;
    @FXML
    public Label sps;
    @FXML
    public Label LabSps;
    @FXML
    public Label LabMax;
    @FXML
    public Label LabMin;
    @FXML
    public Label LabAvg;
    @FXML
    public Label LabVal;    
	
	//資料排入駐列
	private class AddToQueue implements Runnable {
		public void run() {
			//開發期間以亂數
			if(debug){	
				n=(int)(Math.random()*(1350-1320+1))+1320;				
				nv=n-(int)(Math.random()*(50-1+1))+1;
        		mv=n+(int)(Math.random()*(50-1+1))+1;
        		num=pow(mv);
        		vc=(int)(Math.random()*(56-30+1))+30;        		      		
			}else{
			
				num=pow(mv);
			}			
			//每次間隔時間執行工作
			count();
			setTime();//更新時間
			try {				
				//if(!debug){
				saveData();//儲存資料		    	
				//}
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
					val.setText(num+"");					
					yearValue.setText(day);
					timeValue.setText(time);					
			    	
					//更新圖表
					addDataToSeries();
					if(cs==0){addDataToHourSeries();}
					if(cm==0){addDataToDaySeries();}
					if(ch==0){addDataToWeekSeries();}
					if(cd==0){addDataToMonthSeries();}					
				}
			});
		}
	}
	
	//每分鐘圖表
	@FXML
	private NumberAxis xAxis;
	@FXML
	private NumberAxis yAxis;
	
	//private ConcurrentLinkedQueue<Number> dataQ = new ConcurrentLinkedQueue<Number>();	
	private ConcurrentLinkedQueue<Number> dataQMini = new ConcurrentLinkedQueue<Number>();
	private ConcurrentLinkedQueue<Number> dataQMax = new ConcurrentLinkedQueue<Number>();
	//private Series series;
	private Series series_min;
	private Series series_max;
	private int seriesData = 0;
	
	private void addDataToSeries() {		
		for (int i = 0; i < 60; i++) {
			if (dataQMax.isEmpty()){
				break;
			}			
			//series.getData().add(new AreaChart.Data(seriesData++, dataQ.remove()));
			series_min.getData().add(new AreaChart.Data(seriesData++, dataQMini.remove()));			
			series_max.getData().add(new AreaChart.Data(seriesData-1, dataQMax.remove()));
		}
		if (series_min.getData().size() > 60) {
			//series_min.getData().remove(0, (series.getData().size() - 60));
			series_min.getData().remove(0, (series_min.getData().size() - 61));
			series_max.getData().remove(0, (series_max.getData().size() - 61));
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
			if(dataH.isEmpty()){break;}
			series_h.getData().add(new AreaChart.Data(seriesData_h++, dataH.remove()));				
			series_h_min.getData().add(new AreaChart.Data(seriesData_h-1, dataHMini.remove()));			
			series_h_max.getData().add(new AreaChart.Data(seriesData_h-1, dataHMax.remove()));			
		}
		if (series_h.getData().size() > 61){
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
		if(debug){
			interval=1000;
		}else{			
			try{
				gpio = GpioFactory.getInstance();
				provider = new MCP3008GpioProvider(SpiChannel.CS0);				
				//provider = new MCP3x0xGpioProvider(pins, SpiChannel.CS0, speed, resolution, mode);				
				input = gpio.provisionAnalogInputPin(provider, MCP3008Pin.CH0, "CH0");
		        //provider.setEventThreshold(10, input); //恕限值
		        provider.setMonitorInterval(50); //取樣次數間隔
		        gpio.addListener(listener, input);		        
			}catch(Exception e){
				e.printStackTrace();
			}			
		}
		
		//min
		series_min= new AreaChart.Series<Number, Number>();
		series_max= new AreaChart.Series<Number, Number>();
		xAxis.setForceZeroInRange(false);
		xAxis.setAutoRanging(false);
		xAxis.setTickLabelsVisible(false);
		
		sc.setAnimated(false);
		sc.setCreateSymbols(false);
		sc.getData().addAll(series_max, series_min);
		
		//hour
		series_h = new AreaChart.Series<Number, Number>();
		series_h_min= new AreaChart.Series<Number, Number>();
		series_h_max= new AreaChart.Series<Number, Number>();		
		xAxis_h.setForceZeroInRange(false);
		xAxis_h.setAutoRanging(false);
		xAxis_h.setTickLabelsVisible(false);
		sc_h.setAnimated(false);
		sc_h.setCreateSymbols(false);
		sc_h.getData().addAll(series_h_max, series_h, series_h_min);
		
		//day		
		series_d = new AreaChart.Series<Number, Number>();
		series_d_min= new AreaChart.Series<Number, Number>();
		series_d_max= new AreaChart.Series<Number, Number>();
		xAxis_d.setForceZeroInRange(false);
		xAxis_d.setAutoRanging(false);
		xAxis_d.setTickLabelsVisible(false);
		sc_d.setAnimated(false);
		sc_d.setCreateSymbols(false);
		sc_d.getData().addAll(series_d_max, series_d, series_d_min);
		
		//week
		series_w = new AreaChart.Series<Number, Number>();
		series_w_min= new AreaChart.Series<Number, Number>();
		series_w_max= new AreaChart.Series<Number, Number>();
		xAxis_w.setForceZeroInRange(false);
		xAxis_w.setAutoRanging(false);
		xAxis_w.setTickLabelsVisible(false);
		sc_w.setAnimated(false);
		sc_w.setCreateSymbols(false);
		sc_w.getData().addAll(series_w_max, series_w, series_w_min);
		
		//month
		series_m = new AreaChart.Series<Number, Number>();
		series_m_min= new AreaChart.Series<Number, Number>();
		series_m_max= new AreaChart.Series<Number, Number>();
		xAxis_m.setForceZeroInRange(false);
		xAxis_m.setAutoRanging(false);
		xAxis_m.setTickLabelsVisible(false);
		sc_m.setAnimated(false);
		sc_m.setCreateSymbols(false);
		sc_m.getData().addAll(series_m_max, series_m, series_m_min);
		
		executor = Executors.newCachedThreadPool();		
		addToQueue = new AddToQueue();
		executor.execute(addToQueue);	
	}
	
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
		write(list, "log_day.csv");		
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
	
	@FXML
	public DatePicker sysdate;
	@FXML
	public Slider syshh;
	@FXML
	public Slider sysmm;
	@FXML
	public Label syshhVal;
	@FXML
	public Label sysmmVal;
	
	@FXML
	private void setime(ActionEvent event) {
		String s[]=new String[]{"date", "--set=\""+sysdate.getValue(), syshhVal.getText()+":"+sysmmVal.getText()+"\""};
		try{
			
			Runtime.getRuntime().exec(new String[]{"date","--set\"",sysdate.getValue()+" "+syshhVal.getText()+":"+sysmmVal.getText()+"\""});
			System.exit(5);
		}catch(Exception e){
			e.printStackTrace();
			try{
				Runtime.getRuntime().exec(new String[]{"date","--set","2011-12-07 01:20:15.962"});
			}catch(Exception e1){
				e1.printStackTrace();
			}
		}
	}
	@FXML
	private void setSyshhVal(){
		syshhVal.setText((int)syshh.getValue()+"");
	}
	
	@FXML
	private void setSysmmVal(){
		sysmmVal.setText((int)sysmm.getValue()+"");
	}
	
}