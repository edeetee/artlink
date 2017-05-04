package edeetee.artlink;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by edeetee on 3/05/2017.
 */

public class DrawDotsView extends View {
    List<Point> points;
    private Paint pointsPaint;

    public DrawDotsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        pointsPaint = new Paint();
        pointsPaint.setColor(ContextCompat.getColor(getContext(), android.R.color.white
        ));
        pointsPaint.setAlpha(200);

        points = new ArrayList<>();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (Point point : points) {
            canvas.drawCircle(point.x, point.y, 5, pointsPaint);
        }
    }
}
