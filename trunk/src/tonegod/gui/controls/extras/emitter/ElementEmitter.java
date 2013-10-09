/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tonegod.gui.controls.extras.emitter;

import com.jme3.app.Application;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import com.jme3.texture.Texture;
import com.jme3.texture.image.ImageRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import tonegod.gui.controls.extras.SpriteElement;
import tonegod.gui.core.Element;
import tonegod.gui.core.Screen;
import tonegod.gui.framework.animation.Interpolation;
import tonegod.gui.framework.animation.TemporalAction;
import tonegod.gui.framework.core.AnimElement;
import tonegod.gui.framework.core.QuadData;
import tonegod.gui.framework.core.TextureRegion;
import tonegod.gui.framework.core.Transformable;

/**
 *
 * @author t0neg0d
 */
public class ElementEmitter implements Control, Transformable {
	private List<TemporalAction> actions = new ArrayList();
	private Screen screen;
	private Application app;
	private float targetInterval = 1f;
	private float currentInterval = 0f;
	private boolean isEnabled = false;
	private boolean isActive = true;
	private boolean centerVelocity = true;
	protected Map<String, Influencer> influencers = new LinkedHashMap();
	protected ElementParticle[] quads;
	protected AnimElement particles;
	private float emitterWidth, emitterHeight;
	private Vector2f emitterPosition = new Vector2f();
	private Texture tex, emitterShape;
	private ImageRaster ir;
	private ColorRGBA tempColor = new ColorRGBA();
	private Vector2f shapeRatio = new Vector2f(1,1);
	private Interpolation interpolation = Interpolation.linear;
	protected int activeParticleCount = 0;
	
	// Sprite Info
	private String spriteImagePath;
	private int spriteRows, spriteCols, spriteFPS;
	private int spriteWidth, spriteHeight;
	private float spriteSize = 30;
	
	// Globals
	private float minforce = .25f;
	private float maxforce = .25f;
	private float highLife = .5f;
	private float lowLife = .1f;
	
	private Element targetElement = null;
	private Node rootNode = null;
	
	public ElementEmitter(Screen screen, Vector2f position, float emitterWidth, float emitterHeight) {
		this.screen = screen;
		this.app = screen.getApplication();
		this.emitterWidth = emitterWidth;
		this.emitterHeight = emitterHeight;
		this.emitterPosition.set(position);
		
		particles = new AnimElement(app.getAssetManager()) {
			@Override
			public void animElementUpdate(float tpf) {  }
		};
		particles.setOrigin(new Vector2f(0,0));
	//	particles.setPosition(position);
		particles.setRotation(0);
		particles.setScale(1,1);
		
		GravityInfluencer g = new GravityInfluencer();
		addInfluencer(g);
		DirectionInfluencer pd = new DirectionInfluencer();
		addInfluencer(pd);
		ColorInfluencer c = new ColorInfluencer();
		addInfluencer(c);
		SizeInfluencer s = new SizeInfluencer();
		addInfluencer(s);
		RotationInfluencer r = new RotationInfluencer();
		addInfluencer(r);
		ImpulseInfluencer i = new ImpulseInfluencer();
		addInfluencer(i);
		AlphaInfluencer a = new AlphaInfluencer();
		addInfluencer(a);
		SpriteInfluencer sp = new SpriteInfluencer();
		addInfluencer(sp);
	}
	
	/**
	 * Adds the provided influencer to the emitter's influencer chain
	 * @param influencer 
	 */
	public final void addInfluencer(Influencer influencer) {
		influencers.put(influencer.getClass().getName(), influencer);
	}
	
	/**
	 * Returns the first instance of the provided Influencer class
	 * @param c The Influencer class
	 * @return 
	 */
	public <T extends Influencer> T getInfluencer(Class<T> c) {
		return (T) influencers.get(c.getName());
	}
	
	/**
	 * Removes the first instance of the provided Influencer class
	 * @param c Influencer class to remove
	 */
	public void removeInfluencer(Class c) {
		influencers.remove(c.getName());
	}
	
	public void addInfluencer(String key, Influencer influencer) {
		influencers.put(key, influencer);
	}
	
	public void setSprite(String spriteImagePath, int spriteRows, int spriteCols, int spriteFPS) {
		this.spriteImagePath = spriteImagePath;
		this.spriteRows = spriteRows;
		this.spriteCols = spriteCols;
		this.spriteFPS = spriteFPS;
		
		tex = app.getAssetManager().loadTexture(spriteImagePath);
		particles.setTexture(tex);
		
		spriteWidth = tex.getImage().getWidth()/spriteCols;
		spriteHeight = tex.getImage().getHeight()/spriteRows;
		
		for (int x = 0; x < spriteCols; x++) {
			for (int y = 0; y < spriteRows; y++) {
				particles.addTextureRegion("sprite" + (x+y), (int)(spriteWidth*x), (int)(spriteHeight*y), (int)spriteWidth, (int)spriteHeight);
			}
		}
	}
	
	public void setInterpolation(Interpolation interpolation) {
		this.interpolation = interpolation;
	}
	
	public Interpolation getInterpolation() {
		return this.interpolation;
	}
	
	public void setEmitterShape(String texturePath) {
		emitterShape = app.getAssetManager().loadTexture(texturePath);
		ir = ImageRaster.create(emitterShape.getImage());
	}
	
	public void clearEmitterShape() {
		this.emitterShape = null;
	}
	
	@Override
	public void update(float tpf) {
		for (TemporalAction a : actions) {
			a.act(tpf);
		}
		for (TemporalAction a : actions) {
			if (a.getTime() >= a.getDuration()) {
				actions.remove(a);
				break;
			}
		}
		for (ElementParticle p : quads) {
			if (p.active) {
				p.update(tpf);
			}
		}
		particles.update(tpf);
		if (isEnabled) {
			if (isActive) {
				currentInterval += tpf;
				if (currentInterval >= targetInterval) {
					int numParticles = (int)(currentInterval/targetInterval);
					emitNextParticle(numParticles);
					currentInterval -= targetInterval;
				}
			}
		}
	}
	
	public Element getTargetElement() {
		return this.targetElement;
	}
	
	public Node getRootNode() {
		return this.rootNode;
	}
	
	public void setMaxParticles(int maxParticles) {
		quads = new ElementParticle[maxParticles];
		for (int i = 0; i < maxParticles; i++) {
			ElementParticle p = new ElementParticle();
			String key = "sprite" + (FastMath.nextRandomInt(0, particles.getTextureRegions().size()-1));
			TextureRegion region = particles.getTextureRegion(key);
			particles.addQuad(String.valueOf(i), key, new Vector2f(0,0), new Vector2f(region.getRegionWidth()/2,region.getRegionHeight()/2));
			p.particle = particles.getQuads().get(String.valueOf(i)); 
			p.initialize(true);
			quads[i] = p;
		}
		particles.initialize();
	}
	
	public void setParticlesPerSecond(int particlesPerSecond) {
		this.targetInterval = 1f/(float)particlesPerSecond;
	}
	
	public void startEmitter() {
		startEmitter(null);
	}
	
	public void startEmitter(Element targetElement) {
		this.targetElement = targetElement;
		if (targetElement == null)	screen.getGUINode().attachChild(particles);
		else						this.targetElement.attachChild(particles);
		rootNode = screen.getGUINode();
		rootNode.addControl(this);
		this.isEnabled = true;
		update(0.001f);
	}
	
	public void stopEmitter() {
		destroyEmitter();
	}
	
	public void destroyEmitter() {
		this.isEnabled = false;
		if (this.targetElement != null) {
			this.targetElement.detachChild(particles);
			this.targetElement = null;
		} else {
			screen.getGUINode().detachChild(particles);
		}
		try { screen.getGUINode().removeControl(this); }
		catch (Exception ex) {  }
	}
	
	public void setIsActive(boolean isActive) {
		this.isActive = isActive;
	}
	
	private void emitNextParticle(int numParticles) {
		boolean particleEmitted = false;
		for (ElementParticle p : quads) {
			if (!p.particle.getIsVisible() && !particleEmitted) {
				p.initialize(false);
				activeParticleCount++;
				numParticles--;
				if (numParticles == 0) {
					particleEmitted = true;
					break;
				}
			}
		}
	}

	public float getMinForce() {
		return minforce/100f;
	}

	public float getMaxForce() {
		return maxforce/100f;
	}

	public void setForce(float force) {
		this.minforce = force*100f;
		this.maxforce = force*100f;
	}

	public void setMinForce(float minforce) {
		this.minforce = minforce*100f;
	}

	public void setMaxForce(float maxforce) {
		this.maxforce = maxforce*100f;
	}

	public void setMinMaxForce(float minforce, float maxforce) {
		this.minforce = minforce*100f;
		this.maxforce = maxforce*100f;
	}

	public float getHighLife() {
		return highLife;
	}

	public void setHighLife(float highLife) {
		this.highLife = highLife;
	}

	public float getLowLife() {
		return lowLife;
	}

	public void setLowLife(float lowLife) {
		this.lowLife = lowLife;
	}

	public void setLife(float life) {
		this.lowLife = life;
		this.highLife = life;
	}

	public void setLowHighLife(float lowlife, float highlife) {
		this.lowLife = lowlife;
		this.highLife = highlife;
	}

	public AnimElement getParticles() {
		return this.particles;
	}
	
	public ElementParticle getParticle(int index) {
		if (index > -1 && index < quads.length)
			return quads[index];
		else
			return null;
	}
	
	public void removeParticle(int index) {
		if (index > -1 && index < quads.length)
			quads[index].killParticle();
	}
	
	public void removeParticle(ElementParticle p) {
		int index = 0;
		for (ElementParticle particle : quads) {
			if (particle == p) {
				quads[index].killParticle();
				break;
			}
			index++;
		}
	}
	
	public void removeAllParticles() {
		for (ElementParticle p : quads) {
			p.killParticle();
		}
	}
	
	public void emitAllParticles() {
		for (ElementParticle p : quads) {
			if (!p.active) {
				p.initialize(false);
				activeParticleCount++;
			}
		}
	}
	
	public void emitNumParticles(int count) {
		for (ElementParticle p : quads) {
			if (count > 0) {
				if (!p.active) {
					p.initialize(false);
					activeParticleCount++;
					count--;
				}
			} else
				break;
		}
	}
	
	public void setCenterVelocity(boolean center) {
		this.centerVelocity = center;
	}
	
	public int getActiveParticleCount() { return activeParticleCount; }
	
	@Override
	public Control cloneForSpatial(Spatial spatial) {
		return this;
	}

	@Override
	public void setSpatial(Spatial spatial) {
		
	}

	@Override
	public void render(RenderManager rm, ViewPort vp) {
		
	}

	@Override
	public void write(JmeExporter ex) throws IOException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void read(JmeImporter im) throws IOException {
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	@Override
	public void setPositionX(float x) { emitterPosition.setX(x); }

	@Override
	public void setPositionY(float y) { emitterPosition.setY(y); }

	@Override
	public void setPosition(float x, float y) { emitterPosition.set(x,y); }
	
	@Override
	public void setPosition(Vector2f position) { this.emitterPosition.set(position); }
	
	@Override
	public void setRotation(float rotation) {  }

	@Override
	public void setScaleX(float scaleX) { particles.setScaleX(scaleX); }

	@Override
	public void setScaleY(float scaleY) { particles.setScaleY(scaleY); }

	@Override
	public float getPositionX() { return emitterPosition.x; }

	@Override
	public float getPositionY() { return emitterPosition.y; }

	@Override
	public float getRotation() { return particles.getRotation(); }

	@Override
	public float getScaleX() { return particles.getScaleX(); }

	@Override
	public float getScaleY() { return particles.getScaleY(); }

	@Override
	public void addAction(TemporalAction action) {
		action.setTransformable(this);
		this.actions.add(action);
	}
	
	public class ElementParticle {
		public QuadData particle;
		public Vector2f position = new Vector2f();
		public Vector2f velocity = new Vector2f();
		public float randforce;
		public ColorRGBA color = new ColorRGBA();
		public float size = 1;
		public float life;
		public float startlife;
		public float angle;
		public float rotateSpeed;
		public boolean rotateDir;
		public boolean active = false;
		public float blend;
		private Map<String,Object> data = new HashMap();
		private float diffX, diffY;
		
		public void update(float tpf) {
			life -= tpf;
			blend = (startlife - life) / startlife;
			if (interpolation != null) blend = interpolation.apply(blend);
			if (life <= 0) {
				killParticle();
				return;
			}
			
			for (Influencer inf : influencers.values()) {
				if (inf.getIsEnabled())
					inf.update(this, tpf);
			}
			
			particle.setPosition(position);
			particle.setScaleX(size);
			particle.setScaleY(size);
			particle.origin.set(spriteWidth*0.5f,spriteHeight*0.5f);
			particle.color.set(color);
			particle.setRotation(angle);
		};
		
		public void initialize(boolean hide) {
			diffX = FastMath.rand.nextFloat();
		//	if (FastMath.rand.nextBoolean()) diffX = -diffX;
			diffY = FastMath.rand.nextFloat();
		//	if (FastMath.rand.nextBoolean()) diffY = -diffY;
			
			if (emitterShape != null) {
				ir.getPixel((int)(diffX*(emitterShape.getImage().getWidth())),(int)(diffY*(emitterShape.getImage().getHeight())), tempColor);
				while (tempColor.r < 0.2f) {
					diffX = FastMath.rand.nextFloat();
					diffY = FastMath.rand.nextFloat();
					ir.getPixel((int)(diffX*(emitterShape.getImage().getWidth())),(int)(diffY*(emitterShape.getImage().getHeight())), tempColor);
				}
			}
			
			diffX *= emitterWidth;
			diffY *= emitterHeight;
			
			position.set(emitterPosition);
		//	position.x *= emitterWidth;
		//	position.y *= emitterHeight;
			position.addLocal(diffX,diffY);
			
			randforce = (FastMath.nextRandomFloat()*(maxforce-minforce))+minforce;
			if (!centerVelocity) {
				float velX = FastMath.rand.nextFloat()*randforce;
				if (FastMath.rand.nextBoolean()) velX = -velX;
				float velY = FastMath.rand.nextFloat()*randforce;
				if (FastMath.rand.nextBoolean()) velY = -velY;
				velocity.set(velX,velY);
			} else {
				velocity.set(1/emitterWidth*diffX, 1/emitterHeight*diffY);
				velocity.subtractLocal(0.5f,0.5f);
				velocity.multLocal(randforce);
			}
			life = highLife;
			startlife = (highLife - lowLife) * FastMath.nextRandomFloat() + lowLife ;
			life = startlife;
			rotateDir = FastMath.rand.nextBoolean();
			rotateSpeed = FastMath.rand.nextFloat();
			size = 1;
			
			for (Influencer inf : influencers.values()) {
				inf.initialize(this);
			}
			
			active = !hide;
			if (hide)	particle.hide();
			else		particle.show();
			update(0);
		}
		
		public void killParticle() {
			active = false;
			activeParticleCount--;
			particle.hide();
		}
		
		public void putData(String key, Object object) {
			data.put(key, object);
		}
		
		public Object getData(String key) {
			return data.get(key);
		}
	}
}