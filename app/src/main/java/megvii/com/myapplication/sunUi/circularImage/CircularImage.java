package megvii.com.myapplication.sunUi.circularImage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import megvii.com.myapplication.sunUi.SunUiUtil;

public class CircularImage extends MaskedImage {

    public CircularImage(Context paramContext) {
        super(paramContext);
    }

    public CircularImage(Context paramContext, AttributeSet paramAttributeSet) {
        super(paramContext, paramAttributeSet);
    }

    public CircularImage(Context paramContext, AttributeSet paramAttributeSet, int paramInt) {
        super(paramContext, paramAttributeSet, paramInt);
    }

    public Bitmap createMask() {
        Bitmap.Config localConfig = Bitmap.Config.ARGB_8888;
        Bitmap localBitmap = Bitmap.createBitmap(getWidth(), getHeight(), localConfig);
        Canvas localCanvas = new Canvas(localBitmap);
        Paint localPaint = new Paint(1);
        localPaint.setColor(Color.BLACK);
        RectF localRectF;
        if (SunUiUtil.densityRate != 0)
            localRectF = new RectF(0.0F, 0.0F, (int) (getWidth() * (SunUiUtil.densityRate + .001) + .5), (int) (getHeight() * (SunUiUtil.densityRate + .001) + .5));
        else
            localRectF = new RectF(0.0F, 0.0F, getWidth(), getHeight());
        localCanvas.drawOval(localRectF, localPaint);
        return localBitmap;
    }
}