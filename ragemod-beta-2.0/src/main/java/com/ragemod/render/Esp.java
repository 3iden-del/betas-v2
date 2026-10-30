package com.ragemod.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ragemod.core.Config;
import com.ragemod.core.Marks;
import com.ragemod.core.WhitelistGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Colored hitboxes. We project the player boxes to the screen ourselves (using the exact
 * camera matrices captured from the world renderer) and draw them as 2D lines in the HUD layer.
 * This means they are always visible, also through walls.
 */
public final class Esp {
	private static final Matrix4f VIEW = new Matrix4f();
	private static final Matrix4f PROJ = new Matrix4f();
	private static Vec3 camPos = Vec3.ZERO;
	private static float partial = 1.0f;
	private static boolean haveCamera;

	private static final float NEAR = 0.05f;

	private Esp() {}

	/** Called from LevelRendererMixin once per frame, before the world is drawn. */
	public static void capture(Vec3 pos, Matrix4f view, Matrix4f proj, float partialTick) {
		VIEW.set(view);
		PROJ.set(proj);
		camPos = pos;
		partial = partialTick;
		haveCamera = true;
	}

	/** HUD element callback. */
	public static void render(GuiGraphics g, net.minecraft.client.DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!haveCamera || mc.level == null || mc.player == null || mc.options.hideGui) return;
		if (!WhitelistGate.allowed()) return;
		if (!Config.HITBOXES.b()) return;

		ClientLevel level = mc.level;
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		double scale = mc.getWindow().getGuiScale();
		float hbThick = (float) ((Config.HB_THICK.i() + 0.5) / scale);
		double maxDist = Config.ESP_DISTANCE.i();
		double maxDistSq = maxDist * maxDist;

		for (AbstractClientPlayer p : level.players()) {
			if (p == mc.player || p.isSpectator()) continue;
			if (mc.getConnection() == null || mc.getConnection().getPlayerInfo(p.getUUID()) == null) continue; // skip NPCs
			String name = p.getName().getString();

			Integer boxRgb = Marks.boxRgb(name);
			if (boxRgb == null) continue;

			double x = Mth.lerp((double) partial, p.xo, p.getX());
			double y = Mth.lerp((double) partial, p.yo, p.getY());
			double z = Mth.lerp((double) partial, p.zo, p.getZ());
			double dx = x - camPos.x;
			double dy = y - camPos.y;
			double dz = z - camPos.z;
			if (dx * dx + dy * dy + dz * dz > maxDistSq) continue;

			double hw = p.getBbWidth() / 2.0;
			double h = p.getBbHeight();

			if (boxRgb != null) {
				int color = 0xFF000000 | boxRgb;
				float[][] c = new float[8][];
				int i = 0;
				for (int ix = 0; ix < 2; ix++) {
					for (int iy = 0; iy < 2; iy++) {
						for (int iz = 0; iz < 2; iz++) {
							c[i++] = clip(x + (ix == 0 ? -hw : hw), y + (iy == 0 ? 0 : h), z + (iz == 0 ? -hw : hw));
						}
					}
				}
				// corner index = ix*4 + iy*2 + iz
				int[][] edges = {
					{0, 1}, {2, 3}, {4, 5}, {6, 7},
					{0, 2}, {1, 3}, {4, 6}, {5, 7},
					{0, 4}, {1, 5}, {2, 6}, {3, 7}
				};
				for (int[] e : edges) line(g, c[e[0]], c[e[1]], sw, sh, hbThick, color);
			}

		}
	}

	/** World position -> clip space (x, y, z, w). */
	private static float[] clip(double wx, double wy, double wz) {
		Vector4f v = new Vector4f((float) (wx - camPos.x), (float) (wy - camPos.y), (float) (wz - camPos.z), 1.0f);
		v.mul(VIEW);
		v.mul(PROJ);
		return new float[] {v.x, v.y, v.z, v.w};
	}

	private static void line(GuiGraphics g, float[] a, float[] b, int sw, int sh, float thick, int color) {
		if (a[3] < NEAR && b[3] < NEAR) return;
		float ax = a[0];
		float ay = a[1];
		float aw = a[3];
		float bx = b[0];
		float by = b[1];
		float bw = b[3];
		if (aw < NEAR) {
			float t = (NEAR - aw) / (bw - aw);
			ax = ax + (bx - ax) * t;
			ay = ay + (by - ay) * t;
			aw = NEAR;
		} else if (bw < NEAR) {
			float t = (NEAR - bw) / (aw - bw);
			bx = bx + (ax - bx) * t;
			by = by + (ay - by) * t;
			bw = NEAR;
		}
		float x1 = (ax / aw * 0.5f + 0.5f) * sw;
		float y1 = (1.0f - (ay / aw * 0.5f + 0.5f)) * sh;
		float x2 = (bx / bw * 0.5f + 0.5f) * sw;
		float y2 = (1.0f - (by / bw * 0.5f + 0.5f)) * sh;
		if (Math.abs(x1) > 20000 || Math.abs(y1) > 20000 || Math.abs(x2) > 20000 || Math.abs(y2) > 20000) return;
		if ((x1 < -50 && x2 < -50) || (x1 > sw + 50 && x2 > sw + 50) || (y1 < -50 && y2 < -50) || (y1 > sh + 50 && y2 > sh + 50)) return;

		Matrix3x2f pose = new Matrix3x2f(g.pose());
		int minX = (int) Math.floor(Math.min(x1, x2) - thick - 1);
		int minY = (int) Math.floor(Math.min(y1, y2) - thick - 1);
		int maxX = (int) Math.ceil(Math.max(x1, x2) + thick + 1);
		int maxY = (int) Math.ceil(Math.max(y1, y2) + thick + 1);
		ScreenRectangle bounds = new ScreenRectangle(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY)).transformMaxBounds(pose);
		g.guiRenderState.submitGuiElement(new LineState(pose, bounds, null, x1, y1, x2, y2, thick, color));
	}

	/** One thick line, drawn as a quad (triangle fan) by the GUI renderer. */
	record LineState(Matrix3x2f matrix, ScreenRectangle bounds, ScreenRectangle scissorArea,
					 float x1, float y1, float x2, float y2, float thick, int color) implements GuiElementRenderState {
		private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("ragemod", "esp_line"))
			.withUsePipelineDrawModeForGui(true)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_FAN)
			.build();

		@Override
		public void buildVertices(VertexConsumer vertices) {
			float dx = x2 - x1;
			float dy = y2 - y1;
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			if (len < 0.0001f) {
				len = 0.0001f;
			}
			float nx = -dy / len * thick * 0.5f;
			float ny = dx / len * thick * 0.5f;
			vertices.addVertexWith2DPose(matrix, x1 + nx, y1 + ny).setColor(color);
			vertices.addVertexWith2DPose(matrix, x2 + nx, y2 + ny).setColor(color);
			vertices.addVertexWith2DPose(matrix, x2 - nx, y2 - ny).setColor(color);
			vertices.addVertexWith2DPose(matrix, x1 - nx, y1 - ny).setColor(color);
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}

		public RenderPipeline pipeline() {
			return PIPELINE;
		}
	}
}
