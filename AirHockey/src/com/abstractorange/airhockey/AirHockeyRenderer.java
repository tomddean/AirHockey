package com.abstractorange.airhockey;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glViewport;
import static android.opengl.Matrix.invertM;
import static android.opengl.Matrix.multiplyMM;
import static android.opengl.Matrix.multiplyMV;
import static android.opengl.Matrix.rotateM;
import static android.opengl.Matrix.setIdentityM;
import static android.opengl.Matrix.setLookAtM;
import static android.opengl.Matrix.translateM;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.GLSurfaceView.Renderer;

import com.abstractorange.airhockey.android.objects.Mallet;
import com.abstractorange.airhockey.android.objects.Puck;
import com.abstractorange.airhockey.android.objects.Table;
import com.abstractorange.airhockey.android.programs.ColorShaderProgram;
import com.abstractorange.airhockey.android.programs.TextureShaderProgram;
import com.abstractorange.airhockey.android.util.Geometry;
import com.abstractorange.airhockey.android.util.Geometry.Plane;
import com.abstractorange.airhockey.android.util.Geometry.Point;
import com.abstractorange.airhockey.android.util.Geometry.Ray;
import com.abstractorange.airhockey.android.util.Geometry.Sphere;
import com.abstractorange.airhockey.android.util.Geometry.Vector;
import com.abstractorange.airhockey.android.util.MatrixHelper;
import com.abstractorange.airhockey.android.util.TextureHelper;
import com.abstractorange.airhockey1.R;


public class AirHockeyRenderer implements Renderer {
	private final Context context;
	
	private final float[] projectionMatrix = new float[16];
	private final float[] viewMatrix = new float[16];
	private final float[] viewProjectionMatrix = new float[16];
	private final float[] modelViewProjectionMatrix = new float[16];
	private final float[] modelMatrix = new float[16];
	private final float[] invertedViewProjectionMatrix = new float[16];
	
	private final float leftBound = -0.5f;
	private final float rightBound = 0.5f;
	private final float farBound = -0.8f;
	private final float nearBound = 0.8f;
	
	private Table table;
	private Mallet mallet;
	private Puck puck;
	
	private TextureShaderProgram textureProgram;
	private ColorShaderProgram colorProgram;
	
	private int texture;
	
	private boolean malletPressed = false;
	private Point blueMalletPosition;
	private Point previousBlueMalletPosition;
	
	private Point puckPosition;
	private Vector puckVector;
	
	public AirHockeyRenderer(Context context) {
		this.context = context;

		table = new Table();
		mallet = new Mallet(0.08f, 0.15f, 32);
		puck = new Puck(0.06f, 0.02f, 32);

		blueMalletPosition = new Point(0f, mallet.height / 2f, 0.4f);
		puckPosition = new Point(0f, puck.height / 2f, 0f);
		puckVector = new Vector(0f, 0f, 0f);
	}
	
	@Override
	public void onSurfaceCreated(GL10 unused, EGLConfig config) {
		glClearColor(0f, 0f, 0f, 0f);

		textureProgram = new TextureShaderProgram(context);
		colorProgram = new ColorShaderProgram(context);
		
		texture = TextureHelper.loadTexture(context, R.drawable.air_hockey_surface);
	}
	
	@Override
	public void onSurfaceChanged(GL10 arg0, int width, int height) {
		glViewport(0, 0, width, height);

		MatrixHelper.perspectiveM(projectionMatrix, 45, (float)width / (float)height, 1f, 10f);
		setLookAtM(viewMatrix, 0, 0f, 1.2f, 2.2f, 0f, 0f, 0f, 0f, 1f, 0f);
	}
	
	@Override
	public void onDrawFrame(GL10 unused) {
		puckPosition = puckPosition.translate(puckVector);
		if (puckPosition.x < leftBound + puck.radius
				|| puckPosition.x > rightBound - puck.radius) {
			puckVector = new Vector(-puckVector.x, puckVector.y, puckVector.z);
			puckVector = puckVector.scale(0.9f);
		}
		if (puckPosition.z < farBound + puck.radius
				|| puckPosition.z > nearBound - puck.radius) {
			puckVector = new Vector(puckVector.x, puckVector.y, -puckVector.z);
			puckVector = puckVector.scale(0.9f);
		}
		puckPosition = new Point(
				clamp(puckPosition.x, leftBound + puck.radius, rightBound - puck.radius),
				puckPosition.y,
				clamp(puckPosition.z, farBound + puck.radius, nearBound - puck.radius));
		
		puckVector = puckVector.scale(0.99f);
		
		glClear(GL_COLOR_BUFFER_BIT);
		multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
		invertM(invertedViewProjectionMatrix, 0, viewProjectionMatrix, 0);
		
		textureProgram.useProgram();

		// draw the table surface
		positionTableInScene();
		textureProgram.setUniforms(modelViewProjectionMatrix, texture);
		table.bindData(textureProgram);
		table.draw();
		
		colorProgram.useProgram();

		// draw the mallets
		positionObjectInScene(0f, mallet.height / 2f, -0.4f);
		colorProgram.setUniforms(modelViewProjectionMatrix, 1f, 0f, 0f);
		mallet.bindData(colorProgram);
		mallet.draw();

		positionObjectInScene(blueMalletPosition.x, blueMalletPosition.y, blueMalletPosition.z);
		colorProgram.setUniforms(modelViewProjectionMatrix, 0f, 0f, 1f);
		mallet.draw();
		
		// draw the puck
		positionObjectInScene(puckPosition.x, puckPosition.y, puckPosition.z);
		colorProgram.setUniforms(modelViewProjectionMatrix, 0f, 0f, 0f);
		puck.bindData(colorProgram);
		puck.draw();
	}
	
	public void handleTouchPress(float normalizedX, float normalizedY) {
		Ray ray = convertNormalized2DPointToRay(normalizedX, normalizedY);
		
		Sphere malletBoundingSphere = new Sphere(new Point(blueMalletPosition.x, blueMalletPosition.y, blueMalletPosition.z), mallet.height / 2f);

		malletPressed = Geometry.intersects(malletBoundingSphere, ray);
	}

	public void handleTouchDrag(float normalizedX, float normalizedY) {
		if (malletPressed) {
			Ray ray = convertNormalized2DPointToRay(normalizedX, normalizedY);
			Plane plane = new Plane(new Point(0,0,0), new Vector(0,1,0));
			Point touchedPoint = Geometry.intersectionPoint(ray, plane);
			
			previousBlueMalletPosition = blueMalletPosition;
			blueMalletPosition = new Point(
					clamp(touchedPoint.x, leftBound + mallet.radius, rightBound - mallet.radius), 
					mallet.height / 2f, 
					clamp(touchedPoint.z, 0f + mallet.radius, nearBound - mallet.radius));

			checkCollision();
		}
	}
	
	private void checkCollision() {
		float distance = Geometry.vectorBetween(blueMalletPosition, puckPosition).length();
	
		if (distance < (puck.radius + mallet.radius)) {
			puckVector = Geometry.vectorBetween(previousBlueMalletPosition, blueMalletPosition);
/*
			float angle = angleBetween(
					Geometry.vectorBetween(previousBlueMalletPosition, blueMalletPosition),
					Geometry.vectorBetween(blueMalletPosition, puckPosition)
					);
			
			float puckVectorMagnitude = FloatMath.cos(angle);
					//angle between mallet movement and mallet/puck direction
			puckVector = Geometry.vectorBetween(blueMalletPosition, puckPosition).scaleTo(puckVectorMagnitude);
			*/
		}
	}
	
	private float clamp(float value, float min, float max) {
		return Math.min(max, Math.max(value, min));
	}
	
	private Ray convertNormalized2DPointToRay(float normalizedX, float normalizedY) {
		final float[] nearPointNdc = { normalizedX, normalizedY, -1, 1 };
		final float[] farPointNdc = { normalizedX, normalizedY, 1, 1 };
		
		final float[] nearPointWorld = new float[4];
		final float[] farPointWorld = new float[4];
		
		multiplyMV(nearPointWorld, 0, invertedViewProjectionMatrix, 0, nearPointNdc, 0);
		multiplyMV(farPointWorld, 0, invertedViewProjectionMatrix, 0, farPointNdc, 0);
		
		divideByW(nearPointWorld);
		divideByW(farPointWorld);
		
		Point nearPointRay = new Point(nearPointWorld[0], nearPointWorld[1], nearPointWorld[2]);
		Point farPointRay = new Point(farPointWorld[0], farPointWorld[1], farPointWorld[2]);
		
		return new Ray(nearPointRay, Geometry.vectorBetween(nearPointRay, farPointRay));
	}
	
	private void divideByW(float[] vector) {
		vector[0] /= vector[3];
		vector[1] /= vector[3];
		vector[2] /= vector[3];
	}
	
	private void positionTableInScene() {
		setIdentityM(modelMatrix, 0);
		rotateM(modelMatrix, 0, -90f, 1f, 0f, 0f);
		multiplyMM(modelViewProjectionMatrix, 0, viewProjectionMatrix, 0, modelMatrix, 0);
	}
	
	private void positionObjectInScene(float x, float y, float z) {
		setIdentityM(modelMatrix, 0);
		translateM(modelMatrix, 0, x, y, z);
		multiplyMM(modelViewProjectionMatrix, 0, viewProjectionMatrix, 0, modelMatrix, 0);
	}
}
