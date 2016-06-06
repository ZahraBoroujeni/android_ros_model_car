package autoauto.github.com.modelcarlocalization;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.jboss.netty.buffer.ChannelBuffer;
import org.ros.android.BitmapFromCompressedImage;

public class ScaledBitmapFromCompressedImage extends BitmapFromCompressedImage
{
  private int scaleFactor = 1;

  public ScaledBitmapFromCompressedImage(int scale)
  {
    scaleFactor = scale;
  }

  @Override
  public Bitmap call(sensor_msgs.CompressedImage message)
  {
    BitmapFactory.Options opt = new BitmapFactory.Options();
    opt.inSampleSize = scaleFactor;

    ChannelBuffer buffer = message.getData();
    byte[] data = buffer.array();

    return  BitmapFactory.decodeByteArray(data, buffer.arrayOffset(), buffer.readableBytes(), opt);
  }
}
