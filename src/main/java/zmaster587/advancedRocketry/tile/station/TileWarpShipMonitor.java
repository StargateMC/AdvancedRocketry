package zmaster587.advancedRocketry.tile.station;

import com.google.common.base.Predicate;
import com.stargatemc.api.CoreAPI;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import java.util.Random;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import zmaster587.advancedRocketry.achievements.ARAchivements;
import zmaster587.advancedRocketry.api.ARConfiguration;
import zmaster587.advancedRocketry.api.Constants;
import zmaster587.advancedRocketry.api.DataStorage.DataType;
import zmaster587.advancedRocketry.api.dimension.IDimensionProperties;
import zmaster587.advancedRocketry.api.dimension.solar.StellarBody;
import zmaster587.advancedRocketry.api.stations.ISpaceObject;
import zmaster587.advancedRocketry.dimension.DimensionManager;
import zmaster587.advancedRocketry.dimension.DimensionProperties;
import zmaster587.advancedRocketry.inventory.IPlanetDefiner;
import zmaster587.advancedRocketry.inventory.TextureResources;
import zmaster587.advancedRocketry.inventory.modules.ModuleData;
import zmaster587.advancedRocketry.inventory.modules.ModulePanetImage;
import zmaster587.advancedRocketry.inventory.modules.ModulePlanetSelector;
import zmaster587.advancedRocketry.item.ItemData;
import zmaster587.advancedRocketry.item.ItemPlanetIdentificationChip;
import zmaster587.advancedRocketry.network.PacketSpaceStationInfo;
import zmaster587.advancedRocketry.stations.SpaceStationObject;
import zmaster587.advancedRocketry.stations.SpaceObjectManager;
import zmaster587.advancedRocketry.tile.multiblock.TileWarpCore;
import zmaster587.advancedRocketry.util.IDataInventory;
import zmaster587.advancedRocketry.world.util.MultiData;
import zmaster587.libVulpes.LibVulpes;
import zmaster587.libVulpes.client.util.IndicatorBarImage;
import zmaster587.libVulpes.inventory.GuiHandler;
import zmaster587.libVulpes.inventory.GuiHandler.guiId;
import zmaster587.libVulpes.inventory.modules.*;
import zmaster587.libVulpes.network.PacketHandler;
import zmaster587.libVulpes.network.PacketMachine;
import zmaster587.libVulpes.util.EmbeddedInventory;
import zmaster587.libVulpes.util.HashedBlockPosition;
import net.minecraft.util.math.BlockPos;
import zmaster587.libVulpes.util.INetworkMachine;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class TileWarpShipMonitor extends TileEntity implements ITickable, IModularInventory, ISelectionNotify, INetworkMachine, IButtonInventory, IProgressBar, IDataSync, IGuiCallback, IDataInventory, IPlanetDefiner {

	protected ModulePlanetSelector container;
	private ModuleText canWarp;
	DimensionProperties dimCache;
	private SpaceStationObject station;
	private static final int ARTIFACT_BEGIN_RANGE = 4, ARTIFACT_END_RANGE = 8;
	ModulePanetImage srcPlanetImg, dstPlanetImg;
	ModuleSync sync1, sync2, sync3;
	ModuleText srcPlanetText, dstPlanetText, warpFuel, status, warpCapacity, eta;
	int warpCost = -1;
	int dstPlanet, srcPlanet;
	private ModuleTab tabModule;
	private static final byte TAB_SWITCH = 4, STORE_DATA = 10, LOAD_DATA = 20, SEARCH = 5, PROGRAMFROMCHIP = 6;
	private MultiData data;
	private EmbeddedInventory inv;
	private static final int DISTANCESLOT = 0, MASSSLOT = 1, COMPOSITION = 2, PLANETSLOT = 3, MAX_PROGRESS = 1000;
	private ModuleProgress programmingProgress;
	private int progress;

	public TileWarpShipMonitor() {
		tabModule = new ModuleTab(4,0,0,this, 3, new String[]{LibVulpes.proxy.getLocalizedString("msg.warpmon.tab.warp"), LibVulpes.proxy.getLocalizedString("msg.warpmon.tab.data"), LibVulpes.proxy.getLocalizedString("msg.warpmon.tab.tracking")}, new ResourceLocation[][] { TextureResources.tabWarp, TextureResources.tabData, TextureResources.tabPlanetTracking} );
		data = new MultiData();
		data.setMaxData(10000);
		inv = new EmbeddedInventory(9);
		programmingProgress = new ModuleProgress(35, 80, 3, TextureResources.terraformProgressBar, this);
		progress = -1;
	}


	private SpaceStationObject getSpaceObject() {
		if(station == null && world.provider.getDimension() == ARConfiguration.getCurrentConfig().spaceDimId) {
			ISpaceObject object = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords(pos);
			if(object instanceof SpaceStationObject)
				station = (SpaceStationObject) object;
		}
		return station;
	}
        public static double distance(BlockPos pos1, BlockPos pos2) {
            return distance(pos1.getX(),pos1.getY(),pos1.getZ(),pos2.getX(),pos2.getY(),pos2.getZ());
        }
        public static double distanceBetweenDimProps(DimensionProperties props1, DimensionProperties props2) {
            double x1 = props1.orbitalDist*Math.cos((float) props1.orbitTheta);
            double y1 = props1.orbitalDist*Math.sin((float) props1.orbitTheta);
            double x2 = props2.orbitalDist*Math.cos((float) props2.orbitTheta);
            double y2 = props2.orbitalDist*Math.sin((float) props2.orbitTheta);
            return Math.max((int)Math.sqrt(Math.pow((x1 - x2),2) + Math.pow((y1 - y2),2)),1);
        }
        public static double distance(double sx, double sy, double sz, double dx, double dy, double dz) {
            double diffx = 0.0;
            if (sx >= dx) diffx = sx - dx;
            if (dx > sx) diffx = dx - sx;
            double diffy = 0.0;
            if (sy >= dy) diffy = sy - dy;
            if (dy > sy) diffy = dy - sy;
            double diffz = 0.0;
            if (sz >= dz) diffz = sz - dz;
            if (dz > sz) diffz = dz - sz;
            double distance = Math.sqrt(Math.pow(diffx,2) + Math.pow(diffy,2) + Math.pow(diffz,2));
            return distance;
        }
        public static double distanceBetweenStarAndPosition(StellarBody body1, double x, double z) {
            return distance(new BlockPos(body1.getPosX(), 0, body1.getPosZ()), new BlockPos(x, 0, z));
        }
        public static double distanceBetweenStars(StellarBody body1, StellarBody body2) {
            return distance(new BlockPos(body1.getPosX(), 0, body1.getPosZ()), new BlockPos(body2.getPosX(), 0, body2.getPosZ()));
        }
        public static double distanceBetweenDimensions(int dim1, int dim2) {
            DimensionProperties props1 = zmaster587.advancedRocketry.dimension.DimensionManager.getEffectiveDimId(dim1, new BlockPos(0,0,0));
            DimensionProperties props2 = zmaster587.advancedRocketry.dimension.DimensionManager.getEffectiveDimId(dim2, new BlockPos(0,0,0));
            double distance = 0.0;
            if (props1 != null && props2 != null) {
                if (props1 != props2) distance += (0.005 * distanceBetweenDimProps(props1,props2)); // Distance relative to star from eachother.
                if (props1.getStar() != props2.getStar()) distance += (distanceBetweenStars(props1.getStar(), props2.getStar())); // Interstellar.
                System.out.println("Distance between : " + props1.getName() + " and " + props2.getName() + " is: " + distance);
            } else {
                System.out.println("Distance between : " + dim1 + " and " + dim2 + " could not be calculated!");
            }
            return distance;
        }
        
	protected int getTravelCost() {
		if(getSpaceObject() != null) {
			DimensionProperties properties = getSpaceObject().getProperties().getParentProperties();

			DimensionProperties destProperties = DimensionManager.getInstance().getDimensionProperties(getSpaceObject().getDestOrbitingBody());
                        System.out.println("Returning distance as fuel cost: " + (int)TileWarpShipMonitor.distanceBetweenDimensions(properties.getId(), destProperties.getId()));
                        return (int)Math.max(TileWarpShipMonitor.distanceBetweenDimensions(properties.getId(), destProperties.getId()),1);
		}
		return Integer.MAX_VALUE;
	}

	@Override
	public int addData(int maxAmount, DataType type, EnumFacing dir,
			boolean commit) {
		return data.addData(maxAmount, type, dir, commit);
	}

	@Override
	public int extractData(int maxAmount, DataType type, EnumFacing dir,
			boolean commit) {
		return data.extractData(maxAmount, type, dir, commit);
	}
        
        public static String getEnglishTimeFromMs(Long ms) {
            if (ms == null || ms <= 0) return "N/A";
            if (ms < 1000) return (ms + "ms");
            Long seconds = ms / 1000;
            int days = 0;
            int hours = 0;
            int minutes = 0;
            while (seconds >= 86400) {
                days += 1;
                seconds -= 86400;
            }
            while (seconds >= 3600) {
                hours += 1;
                seconds -= 3600;
            }
            while (seconds >= 60) {
                minutes += 1;
                seconds -= 60;
            }
            String returnValue = "";
            if (days > 0) {
                returnValue += (days + "d,");
            }
            if (hours > 0) {
                returnValue += (hours + "h,");            
            }
            if (minutes > 0) {
                returnValue += (minutes + "m,");            
            }
            returnValue += (seconds + "s");
            return returnValue;
        }
        
	@Override
	public List<ModuleBase> getModules(int ID, EntityPlayer player) {
		List<ModuleBase> modules = new LinkedList<ModuleBase>();

		if(ID == guiId.MODULARNOINV.ordinal()) {

			//Front page
			if(tabModule.getTab() == 0) {
				modules.add(tabModule);
				//Don't keep recreating it otherwise data is stale
				if(sync1 == null) {
					sync1 = new ModuleSync(0, this);
					sync2 = new ModuleSync(1, this);
					sync3 = new ModuleSync(2, this);

				}
				modules.add(sync1);
				modules.add(sync2);
				modules.add(sync3);

				ISpaceObject station = getSpaceObject();
				boolean isOnStation = station != null;

				if(world.isRemote)
					setPlanetModuleInfo();

				//Source planet
				int baseX = 10;
				int baseY = 20;
				int sizeX = 70;
				int sizeY = 70;

				if(world.isRemote) {
					modules.add(new ModuleScaledImage(baseX,baseY,sizeX,sizeY, zmaster587.libVulpes.inventory.TextureResources.starryBG));
					modules.add(srcPlanetImg);
                                        ModuleText text = null;           
					if (station.getOrbitingPlanetId() == SpaceObjectManager.WARPDIMID) {
                                            text = new ModuleText(baseX + 4, baseY + 4, "Departed:", 0xFFFFFF);
                                        } else {
                                            text = new ModuleText(baseX + 4, baseY + 4, "Orbiting:", 0xFFFFFF);
                                        }
					text.setAlwaysOnTop(true);
					modules.add(text);
					
					modules.add(srcPlanetText);

					//Border
					modules.add(new ModuleScaledImage(baseX - 3,baseY,3,sizeY, TextureResources.verticalBar));
					modules.add(new ModuleScaledImage(baseX + sizeX, baseY, -3,sizeY, TextureResources.verticalBar));
					modules.add(new ModuleScaledImage(baseX,baseY,70,3, TextureResources.horizontalBar));
					modules.add(new ModuleScaledImage(baseX,baseY + sizeY - 3,70,-3, TextureResources.horizontalBar));
				}
				modules.add(new ModuleButton(baseX - 3, baseY + sizeY, 0, LibVulpes.proxy.getLocalizedString("msg.warpmon.selectplanet"), this,  zmaster587.libVulpes.inventory.TextureResources.buttonBuild, sizeX + 6, 16));


				//Status text
				modules.add(new ModuleText(baseX, baseY + sizeY + 20, LibVulpes.proxy.getLocalizedString("msg.warpmon.corestatus"), 0x1b1b1b));
				boolean flag = isOnStation && getSpaceObject().getFuelAmount() >= getTravelCost() && getSpaceObject().hasUsableWarpCore();
				flag = flag && !(isOnStation && (getSpaceObject().getDestOrbitingBody() == Constants.INVALID_PLANET || getSpaceObject().getOrbitingPlanetId() == getSpaceObject().getDestOrbitingBody()));
				boolean artifactFlag = (dimCache != null && meetsArtifactReq(dimCache));
				canWarp = new ModuleText(baseX, baseY + sizeY + 30, (isOnStation && (getSpaceObject().getDestOrbitingBody() == Constants.INVALID_PLANET || getSpaceObject().getOrbitingPlanetId() == getSpaceObject().getDestOrbitingBody())) ? LibVulpes.proxy.getLocalizedString("msg.warpmon.nowhere") : 
					(!artifactFlag ? LibVulpes.proxy.getLocalizedString("msg.warpmon.missingart") : (flag ? LibVulpes.proxy.getLocalizedString("msg.warpmon.ready") : LibVulpes.proxy.getLocalizedString("msg.warpmon.notready"))), flag && artifactFlag ? 0x1baa1b : 0xFF1b1b);
				modules.add(canWarp);
				modules.add(new ModuleProgress(baseX, baseY + sizeY + 40, 10, new IndicatorBarImage(70, 58, 53, 8, 122, 58, 5, 8, EnumFacing.EAST, TextureResources.progressBars), this));
				//modules.add(new ModuleText(baseX + 82, baseY + sizeY + 20, "Fuel Cost:", 0x1b1b1b));
				warpCost = getTravelCost();
				
				


				//DEST planet
				baseX = 94;
				baseY = 20;
				sizeX = 70;
				sizeY = 70;
				ModuleButton warp = new ModuleButton(baseX - 3, baseY + sizeY,1, LibVulpes.proxy.getLocalizedString("msg.warpmon.warp"), this ,  zmaster587.libVulpes.inventory.TextureResources.buttonBuild, sizeX + 6, 16);

				modules.add(warp);

				if(dimCache == null && isOnStation && station.getOrbitingPlanetId() != SpaceObjectManager.WARPDIMID )
					dimCache = DimensionManager.getInstance().getDimensionProperties(station.getOrbitingPlanetId());

				if(!world.isRemote && isOnStation) {
					PacketHandler.sendToPlayer(new PacketSpaceStationInfo(getSpaceObject().getId(), getSpaceObject()), player);
				}


				if(world.isRemote) {
					warpFuel.setText(LibVulpes.proxy.getLocalizedString("msg.warpmon.fuelcost") + (flag ? String.valueOf(warpCost) : LibVulpes.proxy.getLocalizedString("msg.warpmon.na")));
					warpCapacity.setText(LibVulpes.proxy.getLocalizedString("msg.warpmon.fuel") + (isOnStation ? getSpaceObject().getFuelAmount() : LibVulpes.proxy.getLocalizedString("msg.warpmon.na")));
					eta.setText("ETA: " + (getSpaceObject().getTransitionTime() != -1 ? getEnglishTimeFromMs(getSpaceObject().getTransitionTime() - System.currentTimeMillis()) : LibVulpes.proxy.getLocalizedString("msg.warpmon.na")));
					modules.add(warpFuel);
					modules.add(warpCapacity);
                                        modules.add(eta);

					modules.add(new ModuleScaledImage(baseX,baseY,sizeX,sizeY, zmaster587.libVulpes.inventory.TextureResources.starryBG));
					
					if(dimCache != null && world.isRemote) {
						modules.add(dstPlanetImg);
					}
					
					ModuleText text = new ModuleText(baseX + 4, baseY + 4, LibVulpes.proxy.getLocalizedString("msg.warpmon.dest"), 0xFFFFFF);
					text.setAlwaysOnTop(true);
					modules.add(text);
					modules.add(dstPlanetText);
					
					//Border
					modules.add(new ModuleScaledImage(baseX - 3,baseY,3,sizeY, TextureResources.verticalBar));
					modules.add(new ModuleScaledImage(baseX + sizeX, baseY, -3,sizeY, TextureResources.verticalBar));
					modules.add(new ModuleScaledImage(baseX,baseY,70,3, TextureResources.horizontalBar));
					modules.add(new ModuleScaledImage(baseX,baseY + sizeY - 3,70,-3, TextureResources.horizontalBar));
				}
			}
			else if(tabModule.getTab() == 1) {
				modules.add(tabModule);
				modules.add(new ModuleData(35, 20, 0, this, data.getDataStorageForType(DataType.DISTANCE)));
				modules.add(new ModuleData(75, 20, 1, this, data.getDataStorageForType(DataType.MASS)));
				modules.add(new ModuleData(115, 20, 2, this, data.getDataStorageForType(DataType.COMPOSITION)));
			}
			else {
				modules.add(tabModule);
				modules.add(new ModuleText(65, 20, LibVulpes.proxy.getLocalizedString("msg.warpmon.artifact"), 0x202020));
				modules.add(new ModuleSlotArray(30, 35, this, 4, 5));
				modules.add(new ModuleSlotArray(55, 60, this, 5, 6));
				modules.add(new ModuleSlotArray(80, 35, this, 6, 7));
				modules.add(new ModuleSlotArray(105, 60, this, 7, 8));
				modules.add(new ModuleSlotArray(130, 35, this, 8, 9));

				modules.add(new ModuleButton(50, 117, 3, LibVulpes.proxy.getLocalizedString("msg.warpmon.search"), this,  zmaster587.libVulpes.inventory.TextureResources.buttonBuild, LibVulpes.proxy.getLocalizedString("msg.warpmon.datareq"), 100, 10));
				modules.add(new ModuleButton(50, 127, 4, LibVulpes.proxy.getLocalizedString("msg.warpmon.chip"), this, zmaster587.libVulpes.inventory.TextureResources.buttonBuild,100, 10));
				modules.add(new ModuleTexturedSlotArray(30, 120, this, 3, 4, TextureResources.idChip));
				modules.add(programmingProgress);
			}
		}
		else if (ID == guiId.MODULARFULLSCREEN.ordinal()) {
			//Open planet selector menu
			SpaceStationObject station = getSpaceObject();
			int starId = 0;
			if(station != null)
				starId = station.getProperties().getParentProperties().getStar().getId();
			container = new ModulePlanetSelector(starId, zmaster587.libVulpes.inventory.TextureResources.starryBG, this, this, true);
			container.setOffset(1000, 1000);
			container.setAllowStarSelection(true);
			modules.add(container);
		}

		return modules;
	}

	private void setPlanetModuleInfo() {

		ISpaceObject station = getSpaceObject();
		boolean isOnStation = station != null;
		DimensionProperties location = null;
		String planetName = null;

		if(isOnStation) {
                        if (station.getOrbitingPlanetId() == SpaceObjectManager.WARPDIMID || station.getOrbitingPlanetId() == Constants.INVALID_PLANET) {
                            DimensionProperties properties = DimensionManager.getInstance().getDimensionProperties(station.getPrevOrbitingBody());
                            location = properties;
                            planetName = properties.getName();
                        } else {
                            DimensionProperties properties = DimensionManager.getInstance().getDimensionProperties(station.getOrbitingPlanetId());
                            location = properties;
                            planetName = properties.getName();
                        }
		}
		else {
                        if (location == null || planetName == null) {
                            location = DimensionManager.getInstance().getDimensionProperties(world.provider.getDimension());
                            planetName = DimensionManager.getInstance().getDimensionProperties(world.provider.getDimension()).getName();
                        }
			if(planetName == null)
				planetName = "???";
		}

		boolean flag = isOnStation && getSpaceObject().getFuelAmount() >= warpCost && getSpaceObject().hasUsableWarpCore();

		if(canWarp != null) {
			flag = flag && !(isOnStation && (getSpaceObject().getDestOrbitingBody() == Constants.INVALID_PLANET || getSpaceObject().getOrbitingPlanetId() == getSpaceObject().getDestOrbitingBody()));
			boolean artifactFlag = (dimCache != null && meetsArtifactReq(dimCache));
			
			canWarp.setText(isOnStation && (getSpaceObject().getDestOrbitingBody() == Constants.INVALID_PLANET || 
					getSpaceObject().getOrbitingPlanetId() == getSpaceObject().getDestOrbitingBody()) ? LibVulpes.proxy.getLocalizedString("msg.warpmon.nowhere") : 
				(!artifactFlag ? LibVulpes.proxy.getLocalizedString("msg.warpmon.missingart") : 
					(flag ? LibVulpes.proxy.getLocalizedString("msg.warpmon.ready") : LibVulpes.proxy.getLocalizedString("msg.warpmon.notready"))));
			canWarp.setColor(flag && artifactFlag ? 0x1baa1b : 0xFF1b1b);
		}


		if(world.isRemote) {
			if(srcPlanetImg == null ) {
				//Source planet
				int baseX = 10;
				int baseY = 20;
				int sizeX = 65;
				int sizeY = 65;

				srcPlanetImg = new ModulePanetImage(baseX + 10,baseY + 10,sizeX - 20, location);
				srcPlanetText = new ModuleText(baseX + 4, baseY + 56, "", 0xFFFFFF);
				srcPlanetText.setAlwaysOnTop(true);
				warpFuel = new ModuleText(baseX + 82, baseY + sizeY + 25, "", 0x1b1b1b);
				warpCapacity = new ModuleText(baseX + 82, baseY + sizeY + 35, "", 0x1b1b1b);
				eta = new ModuleText(baseX + 82, baseY + sizeY + 45, "", 0x1b1b1b);

				//DEST planet
				baseX = 94;
				baseY = 20;
				sizeX = 65;
				sizeY = 65;

				dstPlanetImg = new ModulePanetImage(baseX + 10,baseY + 10,sizeX - 20, location);
				dstPlanetText = new ModuleText(baseX + 4, baseY + 56, "", 0xFFFFFF);
				dstPlanetText.setAlwaysOnTop(true);

			}

			srcPlanetImg.setDimProperties(location);
			srcPlanetText.setText(planetName);


			warpFuel.setText(LibVulpes.proxy.getLocalizedString("msg.warpmon.fuelcost") + (warpCost < Integer.MAX_VALUE ? String.valueOf(warpCost) : LibVulpes.proxy.getLocalizedString("msg.warpmon.na")));
			warpCapacity.setText(LibVulpes.proxy.getLocalizedString("msg.warpmon.fuel") + (isOnStation ? ((SpaceStationObject)station).getFuelAmount() : LibVulpes.proxy.getLocalizedString("msg.warpmon.na")));
                        eta.setText("ETA: " + (getSpaceObject().getTransitionTime() != -1 ? getEnglishTimeFromMs(getSpaceObject().getTransitionTime() - System.currentTimeMillis()) : LibVulpes.proxy.getLocalizedString("msg.warpmon.na")));
					


			DimensionProperties dstProps = null;
			if(isOnStation)
				dstProps = DimensionManager.getInstance().getDimensionProperties(dstPlanet);

			if(dstProps != null) {
				planetName = dstProps.getName();
				location = dstProps;


				dstPlanetImg.setDimProperties(location);
				dstPlanetText.setText(planetName);

				dstPlanetImg.setVisible(true);

			}
			else {
				dstPlanetText.setText("???");
				dstPlanetImg.setVisible(false);
			}
		}
	}

	@Override
	public String getModularInventoryName() {
		return "tile.stationmonitor.name";
	}

	@Override
	public boolean canInteractWithContainer(EntityPlayer entity) {
		return true;
	}

	@Override
	public void onInventoryButtonPressed(int buttonId) {
		if(getSpaceObject() != null) {
			if(buttonId == 0)
				PacketHandler.sendToServer(new PacketMachine(this, (byte)0));
			else if(buttonId == 1) {
				PacketHandler.sendToServer(new PacketMachine(this, (byte)2));
			}
			else if(buttonId == 3) {
				PacketHandler.sendToServer(new PacketMachine(this, (byte)SEARCH));
			}
			else if(buttonId == 4) {
				PacketHandler.sendToServer(new PacketMachine(this, (byte)PROGRAMFROMCHIP));
			}
		}
	}

	@Override
	public void writeDataToNetwork(ByteBuf out, byte id) {
		if(id == 1 || id == 3)
			out.writeInt(container.getSelectedSystem());
		else if(id == TAB_SWITCH)
			out.writeShort(tabModule.getTab());
		else if(id >= 10 && id < 20) {
			out.writeByte(id - 10);
		}
		else if(id >= 20 && id < 30) {
			out.writeByte(id - 20);
		}
	}

	//TODO fix warp controller not sending 

	@Override
	public void readDataFromNetwork(ByteBuf in, byte packetId,
			NBTTagCompound nbt) {
		if(packetId == 1 || packetId == 3)
			nbt.setInteger("id", in.readInt());
		else if(packetId == TAB_SWITCH)
			nbt.setShort("tab", in.readShort());
		else if(packetId >= 10 && packetId < 20) {
			nbt.setByte("id", (byte)(in.readByte() - 10));
		}
		else if(packetId >= 20 && packetId < 30) {
			nbt.setByte("id", (byte)(in.readByte() - 20));
		}
	}

        public static String getSpeed(EntityPlayer player) {
                switch (CoreAPI.getContributorTier(player)) {
                    case 6:
                        return "+100%";
                    case 5:
                        return "+75%";
                    case 4:
                        return "+50%";
                    case 3:
                        return "+25%";
                    case 2:
                        return "+15%";
                    case 1:
                        return "+5%";
                    default:
                        return "+0%";
                }
        }
        public static double getTravelTimeMultiplier(EntityPlayer player) {
                switch (CoreAPI.getContributorTier(player)) {
                    case 6:
                        return 0.5; // Divide by two to double speed.
                    case 5:
                        return 0.625; 
                    case 4:
                        return 0.75; // 0.5 is 50% of normal time, which is 100%
                    case 3:
                        return 0.875; // 0.75 is 75% of normal time, so therfore 25% increase
                    case 2:
                        return 0.925; // 0.85 is 85% of normal time, so therefore 15% increase
                    case 1:
                        return 0.95; // 0.95 is 95% of normal time, so therefore 5% increase
                    default:
                        return 1.0; // 1.0 is normal speed.
                }
        }
        
	@Override
	public void useNetworkData(EntityPlayer player, Side side, byte id,
			NBTTagCompound nbt) {
		if(id == 0)
			player.openGui(LibVulpes.instance, guiId.MODULARFULLSCREEN.ordinal(), world, this.getPos().getX(), this.getPos().getY(), this.getPos().getZ());
		else if(id == 1 || id == 3) {
			int dimId = nbt.getInteger("id");

			if(isPlanetKnown(DimensionManager.getInstance().getDimensionProperties(dimId))) {
				container.setSelectedSystem(dimId);
				selectSystem(dimId);
			}

			//Update known planets
			markDirty();
			if(id == 3)
				player.openGui(LibVulpes.instance, guiId.MODULARNOINV.ordinal(), world, this.getPos().getX(), this.getPos().getY(), this.getPos().getZ());
		}
		else if(id == 2) {
			final SpaceStationObject station = getSpaceObject();

			if(station != null && station.hasUsableWarpCore() && station.useFuel(getTravelCost()) != 0 && meetsArtifactReq(DimensionManager.getInstance().getDimensionProperties(station.getDestOrbitingBody()))) {
				System.out.println("Station : " + station.getId() + " is travelling to : " + station.getDestOrbitingBody() + " and will arrive in : " + TileWarpShipMonitor.getEnglishTimeFromMs((long)Math.max((Math.min(getTravelCost()*5, 5000) * getTravelTimeMultiplier(player)),0)));
                                SpaceObjectManager.getSpaceManager().moveStationToBody(station, station.getDestOrbitingBody(), (int)Math.max((Math.min(getTravelCost()*5, 5000) * getTravelTimeMultiplier(player)),0));

				for (EntityPlayer player2 : world.getPlayers(EntityPlayer.class, new Predicate<EntityPlayer>() {
					public boolean apply(EntityPlayer input) {
						return SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords(input.getPosition()) == station;
					};
				})) {
					ARAchivements.ALL_SHE_GOT.trigger((EntityPlayerMP) player2);
					if(!DimensionManager.hasReachedWarp)
						ARAchivements.FLIGHT_OF_PHEONIX.trigger((EntityPlayerMP) player2);
				}

				DimensionManager.hasReachedWarp = true;

				for(HashedBlockPosition vec : station.getWarpCoreLocations()) {
					TileEntity tile = world.getTileEntity(vec.getBlockPos());
					if(tile != null && tile instanceof TileWarpCore) {
						((TileWarpCore)tile).onInventoryUpdated();
					}
				}
			}
		}
		else if(id == TAB_SWITCH && !world.isRemote) {
			tabModule.setTab(nbt.getShort("tab"));
			player.openGui(LibVulpes.instance, GuiHandler.guiId.MODULARNOINV.ordinal(), getWorld(), pos.getX(), pos.getY(), pos.getZ());
		}
		else if(id >= 10 && id < 20) {
			storeData(nbt.getByte("id") + 10);
		}
		else if(id >= 20 && id < 30) {
			loadData(nbt.getByte("id") + 20);
		}
		else if(id == SEARCH) {
			if(progress == -1 && data.getDataAmount(DataType.COMPOSITION) >= 100 && 
					data.getDataAmount(DataType.DISTANCE) >= 100 &&
					data.getDataAmount(DataType.MASS) >= 100)
				progress = 0;
		}
		else if(id == PROGRAMFROMCHIP) {
			SpaceStationObject obj = getSpaceObject();
			if(obj != null) {
				ItemStack stack = getStackInSlot(PLANETSLOT);
				if(stack != null && stack.getItem() instanceof ItemPlanetIdentificationChip) {
					if(DimensionManager.getInstance().isDimensionCreated(((ItemPlanetIdentificationChip)stack.getItem()).getDimensionId(stack)));
					obj.discoverPlanet(((ItemPlanetIdentificationChip)stack.getItem()).getDimensionId(stack));
				}
			}
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		inv.writeToNBT(compound);
		data.writeToNBT(compound);
		compound.setInteger("progress", progress);
		return super.writeToNBT(compound);
	}

	@Override
	public NBTTagCompound getUpdateTag() {
		return writeToNBT(new NBTTagCompound());
	}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		super.readFromNBT(compound);
		inv.readFromNBT(compound);
		data.readFromNBT(compound);
		progress = compound.getInteger("progress");
	}

	@Override
	public void onSelectionConfirmed(Object sender) {
		//Container Cannot be null at this time
		onSelected(sender);
		PacketHandler.sendToServer(new PacketMachine(this, (byte)3));
	}

	@Override
	public void onSelected(Object sender) {
		selectSystem(container.getSelectedSystem());
	}

	private void selectSystem(int id) {
		if(getSpaceObject().getOrbitingPlanetId() == SpaceObjectManager.WARPDIMID || id == SpaceObjectManager.WARPDIMID)
			dimCache = null;
		else {
			dimCache = DimensionManager.getInstance().getDimensionProperties(container.getSelectedSystem());

			ISpaceObject station = SpaceObjectManager.getSpaceManager().getSpaceStationFromBlockCoords(this.getPos());
			if(station != null) {
				station.setDestOrbitingBody(id);
			}
		}
	}

	@Override
	public void onSystemFocusChanged(Object sender) {
		PacketHandler.sendToServer(new PacketMachine(this, (byte)1));
	}


	@Override
	public float getNormallizedProgress(int id) {
		//Screw it, the darn thing will stop updating inv in certain circumstances
		if(world.isRemote) {
			setPlanetModuleInfo();
		}

		return getProgress(id)/(float)getTotalProgress(id);
	}

	@Override
	public void setProgress(int id, int progress) {
		if(id == 10) {
			if(getSpaceObject() != null)
				getSpaceObject().setFuelAmount(progress);
		}
		else if(id == 3) {
			this.progress = progress;
		}
	}

	@Override
	public int getProgress(int id) {
		if(id == 10) {
			if(getSpaceObject() != null)
				return getSpaceObject().getFuelAmount();
		}

		if(id == 0)
			return 30;
		else if(id == 1)
			return 30;
		else if(id == 2)
			return (int) 30;
		else if(id == 3) {
			return progress == -1 ? 0 : progress;
		}
		return 0;
	}

	@Override
	public int getTotalProgress(int id) {
		if(id == 10) {
			if(getSpaceObject() != null)
				return getSpaceObject().getMaxFuelAmount();
		}
		if(dimCache == null)
			return 0;
		if(id == 0)
			return dimCache.getAtmosphereDensity()/2;
		else if(id == 1)
			return dimCache.orbitalDist/2;
		else if(id == 2)
			return (int) (dimCache.gravitationalMultiplier*50);
		else if(id == 3) {
			return MAX_PROGRESS;
		}

		return 0;
	}

	@Override
	public void setTotalProgress(int id, int progress) {
	}


	@Override
	public void setData(int id, int value) {
		//Id: 0, destination planet
		//Id: 1, source planet

		if(id == 2) {
			warpCost = value;
		}
		if(id == 1)
			srcPlanet = value;
		else if (id == 0)
			dstPlanet = value;
		setPlanetModuleInfo();
	}


	@Override
	public int getData(int id) {

		if(id == 2)
			return getTravelCost();

		ISpaceObject station = getSpaceObject();
		boolean isOnStation = station != null;
		if(isOnStation) {
			if(id == 1)
				return station.getOrbitingPlanetId();
			else //id == 1
				return station.getDestOrbitingBody();
		}

		return 0;
	}

	@Override
	public void onModuleUpdated(ModuleBase module) {
		//ReopenUI on server
		PacketHandler.sendToServer(new PacketMachine(this, TAB_SWITCH));
	}


	@Override
	public int getSizeInventory() {
		return inv.getSizeInventory();
	}


	@Override
	public ItemStack getStackInSlot(int index) {
		return inv.getStackInSlot(index);
	}


	@Override
	public ItemStack decrStackSize(int index, int count) {
		return inv.decrStackSize(index, count);
	}


	@Override
	public ItemStack removeStackFromSlot(int index) {
		return inv.removeStackFromSlot(index);
	}


	@Override
	public void setInventorySlotContents(int index, ItemStack stack) {
		inv.setInventorySlotContents(index, stack);

	}


	@Override
	public int getInventoryStackLimit() {
		return inv.getInventoryStackLimit();
	}


	@Override
	public boolean isUsableByPlayer(EntityPlayer player) {
		return true;
	}

	@Override
	public boolean isEmpty() {
		return inv.isEmpty();
	}

	@Override
	public void openInventory(EntityPlayer player) {
		inv.openInventory(player);

	}


	@Override
	public void closeInventory(EntityPlayer player) {
		inv.closeInventory(player);

	}


	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack) {
		return inv.isItemValidForSlot(index, stack);
	}


	@Override
	public int getField(int id) {
		return 0;
	}


	@Override
	public void setField(int id, int value) {

	}


	@Override
	public int getFieldCount() {
		return 0;
	}


	@Override
	public void clear() {

	}


	@Override
	public String getName() {
		return getModularInventoryName();
	}


	@Override
	public boolean hasCustomName() {
		return false;
	}


	@Override
	public void loadData(int id) {
		ItemStack stack = null;
		
		//Use an unused datatype for now
		DataType type = DataType.HUMIDITY;
		
		if(id == 0) 
		{
			stack = inv.getStackInSlot(DISTANCESLOT);
			type = DataType.DISTANCE;
		}
		else if (id == 1)
		{
			stack = inv.getStackInSlot(MASSSLOT);
			type = DataType.MASS;
		}
		else if(id == 2)
		{
			stack = inv.getStackInSlot(COMPOSITION);
			type = DataType.COMPOSITION;
		}

		
		
		if(!stack.isEmpty() && stack.getItem() instanceof ItemData) {
			ItemData item = (ItemData) stack.getItem();
			if(item.getDataType(stack) == type)
				item.removeData(stack, this.addData(item.getData(stack), item.getDataType(stack), EnumFacing.UP, true), type);
		}

		if(world.isRemote) {
			PacketHandler.sendToServer(new PacketMachine(this, (byte)(LOAD_DATA + id)));
		}
	}


	@Override
	public void storeData(int id) {
		ItemStack stack = null;
		DataType type = null;
		if(id == 0) {
			stack = inv.getStackInSlot(DISTANCESLOT);
			type = DataType.DISTANCE;
		}
		else if (id == 1) {
			stack = inv.getStackInSlot(MASSSLOT);
			type = DataType.MASS;
		}
		else if(id == 2) {
			stack = inv.getStackInSlot(COMPOSITION);
			type = DataType.COMPOSITION;
		}

		if(stack != null && stack.getItem() instanceof ItemData) {
			ItemData item = (ItemData) stack.getItem();
			data.extractData(item.addData(stack, data.getDataAmount(type), type), type, EnumFacing.UP, true);
		}

		if(world.isRemote) {
			PacketHandler.sendToServer(new PacketMachine(this, (byte)(STORE_DATA + id)));
		}
	}

	private boolean meetsArtifactReq(DimensionProperties properties) {
		//Make sure we have all the artifacts
		
		if(properties.getRequiredArtifacts().isEmpty())
			return true;
		
		List<ItemStack> list = new LinkedList<ItemStack>(properties.getRequiredArtifacts());
		for(int i = ARTIFACT_BEGIN_RANGE; i <= ARTIFACT_END_RANGE; i++) {
			ItemStack stack2 = getStackInSlot(i);
			if(stack2 != null) {
				Iterator<ItemStack> itr = list.iterator();
				while(itr.hasNext()) {
					ItemStack stackInList = itr.next();
					if(stackInList.getItem().equals(stack2.getItem()) && stackInList.getItemDamage() == stack2.getItemDamage()
							&& ItemStack.areItemStackTagsEqual(stackInList, stack2) && stack2.getCount() >= stackInList.getCount())
						itr.remove();
				}
			}
		}
		
		return list.isEmpty();
	}
	
        public DimensionProperties getDimensionProps() {
            try {
                DimensionProperties props = DimensionManager.getEffectiveDimId(this.world, this.getPos());
                if (props == null || props.equals(DimensionManager.defaultSpaceDimensionProperties)) return null;
                return props;
            } catch (Exception e) {
                return null;
            }
        }
        
        public ArrayList<DimensionProperties> getDimensionsInGalaxy() {
            if (getDimensionProps() == null) new ArrayList<DimensionProperties>();
            return DimensionManager.getInstance().getDimensionsForGalaxy((getDimensionProps().getName().substring(7,9)));   
        }
        
        public ArrayList<DimensionProperties> getDimensionsInSystem() {
            if (getDimensionProps() == null) new ArrayList<DimensionProperties>();
            return DimensionManager.getInstance().getDimensionsForSystem(getDimensionProps().getName().substring(0,5),getDimensionProps().getName().substring(7,9));   
        }
        
        public boolean isInSystem(DimensionProperties props) {
            return (getDimensionsInSystem().contains(props));
        }
        public boolean isInGalaxy(DimensionProperties props) {
            return (getDimensionsInGalaxy().contains(props));
        }
        
        public int getDataAmount() {
            if (data == null) return 0;
            int amount = Math.min(data.getDataAmount(DataType.MASS), data.getDataAmount(DataType.COMPOSITION));
            return Math.min(data.getDataAmount(DataType.DISTANCE), amount);
        }
        
        public int getDimCost(DimensionProperties props) {
            int cost = 2000;
            if (isInSystem(props)) cost = 50;
            if (!isInSystem(props) && isInGalaxy(props)) cost = 200;
            if (!isInSystem(props) && !isInGalaxy(props)) cost = 1200;
            return cost;   
        }
        
	@Override
	public void update() {
		if(!world.isRemote && progress != -1) {
			progress++;
                        int cost = 50;
			if(progress >= MAX_PROGRESS) {
				//Do the thing
                                Random r = new Random(); 
				SpaceStationObject obj = getSpaceObject();
				if(Math.abs(world.rand.nextInt()) % ARConfiguration.getCurrentConfig().planetDiscoveryChance == 0 && obj != null) {
					ItemStack stack = getStackInSlot(PLANETSLOT);
					if(stack != null && stack.getItem() instanceof ItemPlanetIdentificationChip) {
						ItemPlanetIdentificationChip item = (ItemPlanetIdentificationChip)stack.getItem();
						List<Integer> unknownPlanets = new LinkedList<Integer>();
						//Check to see if any planets with artifacts can be discovered
						for(int id : DimensionManager.getInstance().getRegisteredDimensions()) {
							DimensionProperties props = DimensionManager.getInstance().getDimensionProperties(id);
							if(!isPlanetKnown(props) && !props.getRequiredArtifacts().isEmpty()) {
								//If all artifacts are met, then add
								if(meetsArtifactReq(props)) {
                                                                        if (isInSystem(props) && r.nextFloat() < 0.6) continue; // 40% chance
                                                                        if (!isInSystem(props) && isInGalaxy(props) && r.nextFloat() < 0.8) continue; // 20% chance
                                                                        if (!isInSystem(props) && !isInGalaxy(props) && r.nextFloat() == 1.0) continue; // 10% chance
                                                                        if (getDimCost(props)+cost <= getDataAmount()) {
                                                                            unknownPlanets.add(id);
                                                                        }
                                                                }
							}
						}

						//if there are not any planets requiring artifacts then get the regular planets
						if(unknownPlanets.isEmpty()) {
							for(int id : DimensionManager.getInstance().getRegisteredDimensions()) {
								DimensionProperties props = DimensionManager.getInstance().getDimensionProperties(id);
								if(!isPlanetKnown(props) && props.getRequiredArtifacts().isEmpty()) {
                                                                        if (isInSystem(props) && r.nextFloat() < 0.6) continue; // 40% chance
                                                                        if (!isInSystem(props) && isInGalaxy(props) && r.nextFloat() < 0.8) continue; // 20% chance
                                                                        if (!isInSystem(props) && !isInGalaxy(props) && r.nextInt(100) == 50) continue; // 1% chance
                                                                        if (getDimCost(props)+cost <= getDataAmount()) {
                                                                            unknownPlanets.add(id);
                                                                        }
								}
							}
						}

						if(!unknownPlanets.isEmpty()) {
							int newId = (int)(world.rand.nextFloat()*unknownPlanets.size());
							newId = unknownPlanets.get(newId);
							item.setDimensionId(stack, newId);
                                                        DimensionProperties found = DimensionManager.getInstance().getDimensionProperties(newId);
                                                        if (found == null) {
                                                            System.out.println("Found dimension: " + newId + ", but its not valid!");
                                                        } else {
                                                            cost += getDimCost(found);
                                                            obj.discoverPlanet(newId);
                                                            System.out.println("Found dimension: " + found.getName() + ", cost: " + cost);
                                                        }
						}
					}
				}
				data.extractData(cost, DataType.COMPOSITION, EnumFacing.UP, true);
				data.extractData(cost, DataType.DISTANCE, EnumFacing.UP, true);
				data.extractData(cost, DataType.MASS, EnumFacing.UP, true);
				progress = -1;
			}
		}

	}


	@Override
	public boolean isPlanetKnown(IDimensionProperties properties) {
		SpaceStationObject obj = getSpaceObject();
		if(obj != null)
			return obj.isPlanetKnown(properties);
		return false;
	}


	@Override
	public boolean isStarKnown(StellarBody body) {
		SpaceStationObject obj = getSpaceObject();
		if(obj != null)
			return obj.isStarKnown(body);
		return false;
	}
}
