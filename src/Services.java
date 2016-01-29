import java.util.concurrent.ConcurrentLinkedQueue;

import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Series;

public class Services {
	
	private void addDataToHourSeries(ConcurrentLinkedQueue<Number> data, Series series, NumberAxis xAxis, int xSeriesData) {
		for (int i = 0; i < 60; i++) {
			if (data.isEmpty()){
				break;
			}
				
			series.getData().add(new AreaChart.Data(xSeriesData++, data.remove()));
		}
		if (series.getData().size() > 60) {
			series.getData().remove(0, (series.getData().size() - 60));
		}
		// update
		xAxis.setLowerBound(xSeriesData - 60);
		xAxis.setUpperBound(xSeriesData - 1);
	}

}
