package Reika.ChromatiCraft.Render;

import java.util.Random;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import Reika.ChromatiCraft.Registry.ChromaIcons;
import Reika.DragonAPI.Instantiable.Spline;
import Reika.DragonAPI.Instantiable.Spline.SplineAnchor;
import Reika.DragonAPI.Instantiable.Spline.SplineType;
import Reika.DragonAPI.Instantiable.Data.Immutable.DecimalPosition;
import Reika.DragonAPI.Libraries.IO.ReikaColorAPI;
import Reika.DragonAPI.Libraries.IO.ReikaTextureHelper;
import Reika.DragonAPI.Libraries.Java.ReikaGLHelper.BlendMode;
import Reika.DragonAPI.Libraries.Java.ReikaRandomHelper;
import Reika.DragonAPI.Libraries.MathSci.ReikaPhysicsHelper;

public class GlowKnot {

	private static final Random rand = new Random();

	private final Spline spline;

	public final int density;
	public final double size;

	public GlowKnot(double size) {
		density = 48;
		this.size = size;
		spline = new Spline(SplineType.CENTRIPETAL);

		for (int i = 0; i < density; i++) {
			double phi = rand.nextDouble()*360;
			double theta = rand.nextDouble()*360;
			spline.addPoint(new KnotPoint(size, phi, theta, size));
		}
	}

	public void render(double x, double y, double z, int color) {
		GL11.glDisable(GL11.GL_LIGHTING);
		if (MinecraftForgeClient.getRenderPass() == 1) {
			Tessellator v5 = Tessellator.instance;
			spline.render(v5, 0.5, 0.5, 0.5, color, true, true);

			IIcon ico = ChromaIcons.FADE.getIcon();
			float u = ico.getMinU();
			float v = ico.getMinV();
			float du = ico.getMaxU();
			float dv = ico.getMaxV();
			BlendMode.ADDITIVEDARK.apply();
			ReikaTextureHelper.bindTerrainTexture();
			GL11.glPushMatrix();
			GL11.glTranslated(0.5, 0.5, 0.5);

			RenderManager rm = RenderManager.instance;
			double dx = x-RenderManager.renderPosX;
			double dy = y-RenderManager.renderPosY;
			double dz = z-RenderManager.renderPosZ;
			double[] angs = ReikaPhysicsHelper.cartesianToPolar(dx, dy, dz);
			GL11.glRotated(angs[2], 0, 1, 0);
			GL11.glRotated(90-angs[1], 1, 0, 0);

			double d = 1.25;

			v5.startDrawingQuads();
			int a = 160;
			v5.setColorRGBA_I(ReikaColorAPI.getColorWithBrightnessMultiplier(color, a/255F), a);
			v5.addVertexWithUV(-d, -d, 0, u, v);
			v5.addVertexWithUV(d, -d, 0, du, v);
			v5.addVertexWithUV(d, d, 0, du, dv);
			v5.addVertexWithUV(-d, d, 0, u, dv);
			v5.draw();

			BlendMode.DEFAULT.apply();
			GL11.glPopMatrix();
		}
		spline.update();
		GL11.glEnable(GL11.GL_LIGHTING);
	}

	private static class KnotPoint implements SplineAnchor {

		private double radius;
		private double theta;
		private double phi;

		public final double maxSize;

		private double targetRadius;
		private double targetTheta;
		private double targetPhi;

		private KnotPoint(double r, double t, double p, double size) {
			radius = r;
			theta = t;
			phi = p;
			maxSize = size;

			this.pickNewTarget();
		}

		@Override
		public DecimalPosition asPosition() {
			double[] dat = ReikaPhysicsHelper.polarToCartesian(radius, theta, phi);
			return new DecimalPosition(dat[0], dat[1], dat[2]);
		}

		@Override
		public void update() {
			double dr = targetRadius-radius;
			double dt = targetTheta-theta;
			double dp = targetPhi-phi;

			if (this.atTarget(dr, dt, dp)) {
				this.pickNewTarget();
			}

			this.move(dr, dt, dp);
		}

		private void move(double dr, double dt, double dp) {
			radius += 0.025*Math.signum(dr);//Math.max(0.0125, Math.abs(dr)*0.03125*0.03125*0.03125)*Math.signum(dr);
			theta += 0.25*Math.signum(dt);//Math.max(0.125, Math.abs(dt)*0.03125*0.03125*0.03125)*Math.signum(dt);
			phi += 0.25*Math.signum(dp);//Math.max(0.125, Math.abs(dp)*0.03125*0.03125*0.03125)*Math.signum(dp);
		}

		private boolean atTarget(double dr, double dt, double dp) {
			return Math.abs(dr) < 0.05 && Math.abs(dt) < 1 && Math.abs(dp) < 1;
		}

		private void pickNewTarget() {
			targetRadius = ReikaRandomHelper.getRandomPlusMinus(maxSize, maxSize/16D);//rand.nextDouble()*maxSize;
			targetTheta = rand.nextDouble()*360;
			targetPhi = rand.nextDouble()*360;
		}
	}

}