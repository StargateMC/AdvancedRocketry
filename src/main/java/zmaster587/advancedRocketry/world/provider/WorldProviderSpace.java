package zmaster587.advancedRocketry.world.provider;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import zmaster587.advancedRocketry.AdvancedRocketry;
import zmaster587.advancedRocketry.api.AdvancedRocketryBiomes;
import zmaster587.advancedRocketry.api.ARConfiguration;
import zmaster587.advancedRocketry.api.stations.ISpaceObject;
import zmaster587.advancedRocketry.client.render.planet.RenderSpaceSky;
import zmaster587.advancedRocketry.client.render.planet.RenderSpaceTravelSky;
import zmaster587.advancedRocketry.dimension.DimensionManager;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.entity.EntityRocket;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.world.ChunkProviderSpace;

public class WorldProviderSpace extends WorldProviderPlanet {
	private IRenderHandler skyRender;
	
	@Override
	public double getHorizon() {
		return 0;
	}
	
	//TODO: figure out celestial angle from coords
	
	@Override
	public boolean isPlanet() {
		return false;
	}
	@Override
	public String getSaveFolder() {		
			switch (this.getDimension()) {
				case -2:
					return "Space";
				case -9000:
					return "RP_Midway";
				case -9001:
					return "RP_Destiny";
				case -9002:
					return "RP_Beliskner";
				case -9003:
					return "RP_OriMotherShip";
				case -9004:
					return "RP_Aurora";
				case -9005:
					return "RP_ReplicatorShip";
				case -9006:
					return "RP_Daedalus";
				case -9007:
					return "RP_Hatak";
				case -9008:
					return "RP_Hatak";
				default:
					return "ERROR";
							
			}
	}
	
	public int getAverageGroundLevel() {
		return 0;
	}
	
	@Override
	public IChunkGenerator createChunkGenerator() {
		return new ChunkProviderSpace(this.world, this.world.getSeed());
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public IRenderHandler getSkyRenderer() {
		
		//Maybe a little hacky
		EntityPlayerSP player = Minecraft.getMinecraft().player;
		if(player != null)
		{
			Entity e = player.getRidingEntity();
			if(e instanceof EntityRocket)
			{
				if(((EntityRocket)e).getInSpaceFlight())
				{
					if(!(skyRender instanceof RenderSpaceTravelSky))
						skyRender = new RenderSpaceTravelSky();
					return skyRender;
				}
			}
		}
		
		
		if(ARConfiguration.getCurrentConfig().stationSkyOverride)
			return (skyRender == null || !(skyRender instanceof RenderSpaceSky)) ? skyRender = new RenderSpaceSky() : skyRender;
		
		return super.getSkyRenderer();
	}
	
	@Override
	public float getAtmosphereDensity(BlockPos pos) {
		return 0;
	}
	
	@Override
	public float calculateCelestialAngle(long worldTime, float p_76563_3_) {
		return AdvancedRocketry.proxy.calculateCelestialAngleSpaceStation();
	}
	
	@Override
	protected void init() {
		// TODO Auto-generated method stub
		//super.createBiomeProvider();
		this.hasSkyLight=true;
		world.getWorldInfo().setTerrainType(AdvancedRocketry.spaceWorldType);
		
		this.biomeProvider = new BiomeProviderSingle(AdvancedRocketryBiomes.spaceBiome);//new ChunkManagerPlanet(worldObj, worldObj.getWorldInfo().getGeneratorOptions(), DimensionManager.getInstance().getDimensionProperties(worldObj.provider.getDimension()).getBiomes());
		
	}
	
	
	
	@Override
	public DimensionProperties getDimensionProperties(BlockPos pos) {
		ISpaceObject object = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords(pos);
		if(object != null)
			return (DimensionProperties)object.getProperties();
		return DimensionManager.defaultSpaceDimensionProperties;
	}
}
