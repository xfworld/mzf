package com.zonrong.inventory.service;

import com.zonrong.basics.rawmaterial.service.RawmaterialService;
import com.zonrong.common.utils.MzfEntity;
import com.zonrong.common.utils.MzfEnum;
import com.zonrong.common.utils.MzfEnum.BizType;
import com.zonrong.common.utils.MzfEnum.RawmaterialType;
import com.zonrong.common.utils.MzfEnum.StorageType;
import com.zonrong.common.utils.MzfEnum.TargetType;
import com.zonrong.common.utils.MzfUtils;
import com.zonrong.core.exception.BusinessException;
import com.zonrong.core.log.BusinessLogService;
import com.zonrong.core.security.IUser;
import com.zonrong.entity.service.EntityService;
import com.zonrong.inventory.DosingBom;
import com.zonrong.metadata.EntityMetadata;
import com.zonrong.metadata.service.MetadataProvider;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.Map.Entry;

/**
 * date: 2010-11-3
 *
 * version: 1.0
 * commonts: ......
 */

/**
 * 原料库存
 */
@Service
public class RawmaterialInventoryService {
	private Logger logger = Logger.getLogger(this.getClass());

	@Resource
	private MetadataProvider metadataProvider;
	@Resource
	private EntityService entityService;
	@Resource
	private InventoryService inventoryService;
	@Resource
	private RawmaterialService rawmaterialService;
	@Resource
	private BusinessLogService businessLogService;

    /**
     * 裸石入库
     *
     * @param bizType 业务类型
     * @param diamondId 裸石ID
     * @param remark 备注
     * @return 库存ID
     *
     * @throws BusinessException
     */
    public int warehouseDiamond(BizType bizType, int diamondId, String remark, IUser user) throws BusinessException {
		StorageType storageType = StorageType.rawmaterial_nakedDiamond;

		//入库
		Map<String, Object> inventory = new HashMap<String, Object>();
		inventory.put("targetType", TargetType.rawmaterial);
		inventory.put("targetId", diamondId);

		int id = inventoryService.createInventory(inventory, user.getOrgId(), new BigDecimal(1), storageType, user);

		inventoryService.createFlow(bizType, user.getOrgId(),
				new BigDecimal(1), MzfEnum.InventoryType.warehouse, storageType,
				TargetType.rawmaterial, Integer.toString(diamondId), null,
				remark, user);

		return id;
	}

	public void warehouseGold(BizType bizType, int rawmaterialId,BigDecimal quantity, BigDecimal cost, String remark, IUser user)
			throws BusinessException {
		StorageType storageType = StorageType.rawmaterial_gold;
		warehouseByQuantity(bizType, storageType, rawmaterialId, quantity, cost, remark, user);
	}

	public void warehouseGravel(BizType bizType, int rawmaterialId,
			BigDecimal quantity, BigDecimal cost, BigDecimal weight, String remark, IUser user)
			throws BusinessException {
		StorageType storageType = StorageType.rawmaterial_gravel;
        remark = remark + "|" + weight.round(new MathContext(4));
		warehouseByQuantity(bizType, storageType, rawmaterialId, quantity, cost, remark, user);

		//更新碎石重量
		//rawmaterialService.addWeight(rawmaterialId, weight, user);

        //更新库存重量
        Map<String, Object> inventory = getInventory(rawmaterialId, user.getOrgId(), user);
        String inventoryId = MapUtils.getString(inventory, "inventoryId");
        Float dbWeight = MapUtils.getFloat(inventory,"weight",0f);
        Map<String,Object> field = new HashMap<String, Object>();
        field.put("weight",dbWeight+weight.floatValue());
        entityService.updateById(MzfEntity.INVENTORY,inventoryId,field,user);
	}

	public void warehouseParts(BizType bizType, int rawmaterialId,
			BigDecimal quantity, BigDecimal cost, String remark, IUser user)
			throws BusinessException {
		StorageType storageType = StorageType.rawmaterial_parts;
		warehouseByQuantity(bizType, storageType, rawmaterialId, quantity, cost, remark, user);
	}

	private void warehouseByQuantity(BizType bizType, StorageType storageType,
			int rawmaterialId, BigDecimal quantity, BigDecimal cost, String remark, IUser user)
			throws BusinessException {
		if (storageType == null) {
			throw new BusinessException("未指定仓库类型");
		}
		Map<String, Object> inventory = getInventory(rawmaterialId, user.getOrgId(), user);
		Integer inventoryId;
		if (inventory == null) {
			inventoryId = inventoryService.createRawmaterialInventory(rawmaterialId, user.getOrgId(), storageType, user);
		} else {
			inventoryId = MapUtils.getInteger(inventory, "inventoryId");
		}

		inventoryService.warehouse(bizType, inventoryId, quantity, cost, remark, user);

		//rawmaterialService.addCost(rawmaterialId, cost, user);
	}

	/**
	 * 裸石强制出库
     *
	 * @throws BusinessException
	 */
	public void deliveryDiamond(BizType bizType, int rawmaterialId, String remark, IUser user) throws BusinessException {
		StorageType storageType = StorageType.rawmaterial_nakedDiamond;
		Map<String, Object> dbInventory = getInventory(rawmaterialId, user.getOrgId(), user);
		Integer inventoryId = MapUtils.getInteger(dbInventory, "inventoryId");

		EntityMetadata metadata = inventoryService.getEntityMetadataOfInventory();
		int row = entityService.deleteById(metadata, Integer.toString(inventoryId), user);
		if (row == 0) {
			throw new BusinessException("原料(钻石)[" + inventoryId + "]库存出库失败");
		}

		inventoryService.createFlow(bizType, user.getOrgId(),
				new BigDecimal(1), MzfEnum.InventoryType.delivery, storageType,
				TargetType.rawmaterial, Integer.toString(rawmaterialId), null,
				remark, user);
	}

    public void deliveryGravel(BizType bizType,int gravelId,Integer quantity,Float weight,String remark,IUser user) throws BusinessException {
        Map<String,Object> dbInventory = getInventory(gravelId,user.getOrgId(),user);
        Integer inventoryId = MapUtils.getInteger(dbInventory,"inventoryId");

        Integer dbQuantity = MapUtils.getInteger(dbInventory,"quantity");
        Integer dbLockedQuantity = MapUtils.getInteger(dbInventory,"lockedQuantity");
        Float dbWeight = MapUtils.getFloat(dbInventory,"weight");
        //Float dbLockedWeight = MapUtils.getFloat(dbInventory,"lockedWeight");

        if(quantity> dbQuantity - dbLockedQuantity){
            throw new BusinessException("库存数量不足");
        }

        if(weight > dbWeight){
            throw new BusinessException("库存重量不足");
        }

        Double dbCost = MapUtils.getDouble(dbInventory, "cost");
        Double cost = dbCost * weight / dbWeight;

        dbInventory.clear();

        dbInventory.put("quantity",dbQuantity - quantity);
        dbInventory.put("weight",dbWeight - weight);
        dbInventory.put("cost",dbCost - cost);

        entityService.updateById(MzfEntity.INVENTORY,inventoryId+"",dbInventory,user);

//        inventoryService.createFlow(bizType,user.getOrgId(),new BigDecimal(quantity), MzfEnum.InventoryType.delivery,
//                StorageType.rawmaterial_gravel,TargetType.rawmaterial,gravelId+"",cost,remark,user);

        Map<String, Object> flow = new HashMap<String, Object>();

        flow.put("bizType", bizType);
        flow.put("orgId", user.getOrgId());
        flow.put("storageType", MzfEnum.StorageType.rawmaterial_gravel);
        flow.put("type", MzfEnum.InventoryType.delivery);
        flow.put("targetType", MzfEnum.TargetType.rawmaterial);
        flow.put("targetId", gravelId);
        flow.put("remark", remark);
        flow.put("quantity", quantity);
        flow.put("cuserId", user.getId());
        flow.put("cdate", null);
        flow.put("weight",weight);
        flow.put("cost",cost);
        entityService.create(MzfEntity.INVENTORY_FLOW, flow, user);
    }

    /**
     * 配件出库
     *
     * @param bizType 业务类型
     * @param partsId 配件ID
     * @param quantity 数量
     * @param remark 备注
     */
    public void deliveryParts(BizType bizType,int partsId,Integer quantity,String remark,IUser user) throws BusinessException {
        Map<String,Object> inv = getInventory(partsId,user.getOrgId(),user);
        if(MapUtils.isEmpty(inv)){
            throw new BusinessException("配件库存不存在，配件ID:["+partsId+"]");
        }

        int totalQuantity = MapUtils.getIntValue(inv,"quantity");
//        int lockedQuantity = MapUtils.getIntValue(inv,"lockedQuantity");
//
//        if(quantity>totalQuantity - lockedQuantity){
//            throw new BusinessException("可用数量不足");
//        }
//
//        inv.put("totalQuantity",totalQuantity-quantity);
//
        Integer inventoryId = MapUtils.getInteger(inv,"inventoryId");
        Float totalCost = MapUtils.getFloat(inv,"cost2",0f);
        Float cost = totalCost * quantity / totalQuantity;
        inventoryService.delivery(bizType,inventoryId,new BigDecimal(quantity),new BigDecimal(cost),remark,user);
    }

	/**
	 * 提交委外订单时配料出库
	 *
	 * @throws BusinessException
	 */
	public void deliveryOnOem(BizType bizType, Map<Integer, DosingBom> rawmaterialQuantityMap, int orgId, String remark, IUser user) throws BusinessException {
        Set<Integer> rids = rawmaterialQuantityMap.keySet();
        Integer[] rawmaterialIds = rids.toArray(new Integer[rids.size()]);
		List<Map<String, Object>> list = list(rawmaterialIds, orgId, user);
		for (Map<String, Object> map : list) {
			Integer rawmaterialId = MapUtils.getInteger(map, "id");
			String num = MapUtils.getString(map, "num");
			Double dbCost = MapUtils.getDouble(map, "cost");
			Double dbQuantity = MapUtils.getDouble(map, "quantity");
			Double dbLockedQuantity = MapUtils.getDouble(map, "lockedQuantity");
			if (dbCost == null || dbCost < 0) {
				throw new BusinessException("原料[" + num + "]价格为空或低于0");
			}
			if (dbQuantity == null || dbQuantity < 0) {
				throw new BusinessException("原料[" + num + "]数量为空或低于0");
			}
			Double allQuantity = dbQuantity;
			if (dbLockedQuantity != null) {
				allQuantity = allQuantity + dbLockedQuantity;
			}


			//加权平均，更新原料价格
			DosingBom dosingBom = rawmaterialQuantityMap.get(rawmaterialId);
			BigDecimal quantity = dosingBom.getQuantity();
			BigDecimal outCost = new BigDecimal(MzfUtils.getAvgByWeighted(allQuantity, dbCost, quantity));
			outCost = outCost.multiply(new BigDecimal(-1));
			rawmaterialService.addCost(rawmaterialId, outCost, user);

			RawmaterialType type = MzfEnum.RawmaterialType.valueOf(MapUtils.getString(map, "type"));
			if (type == MzfEnum.RawmaterialType.gravel) {
				BigDecimal weight = dosingBom.getWeight();
				if (weight == null) {
					throw new BusinessException("未指定碎石出库重量");
				}
				weight = weight.multiply(new BigDecimal(-1));
				rawmaterialService.addWeight(rawmaterialId, weight, user);
                remark = remark + "|" + weight;
			}

			Integer inventoryId = MapUtils.getInteger(map, "inventoryId");
			inventoryService.deliveryLocked(bizType, inventoryId, quantity, null, remark, user);
		}

		//将原料成本记在商品上（待定）
	}

	public void deliveryById(BizType bizType, BigDecimal quantity, BigDecimal weight, int rawmaterialId, String remark, IUser user) throws BusinessException {
        //TODO:此处实现有问题，应该使用库存ID
		List<Map<String, Object>> list = list(new Integer[]{rawmaterialId}, 1, user);
		Map<String, Object> map;

        if(CollectionUtils.isEmpty(list)){
            throw new BusinessException("原料库存记录为空，原料ID：["+rawmaterialId+"]");
        }

        if(list.size()>1){
            throw new BusinessException("原料库存记录不唯一，原料ID：["+rawmaterialId+"]");
        }

        map = list.get(0);
        //获取原料库存数据

		Double dbCost = MapUtils.getDouble(map, "cost",0d);
		Double dbQuantity = MapUtils.getDouble(map, "quantity",0d);
		Double dbLockedQuantity = MapUtils.getDouble(map, "lockedQuantity",0d);

		if (dbCost <= 0) {
			throw new BusinessException("库存成本异常，为：["+dbCost+"]");
		}
		if (dbQuantity < quantity.doubleValue()) {
			throw new BusinessException("库存数量不足，为：["+dbQuantity+"]");
		}

		Double allQuantity = dbQuantity;
		if (dbLockedQuantity != null) {
			allQuantity = allQuantity + dbLockedQuantity;
		}

		// 加权平均，计算本次出库原料成本
		Double outCost = MzfUtils.getAvgByWeighted(allQuantity, dbCost,quantity);

        Map<String,Object> value=new HashMap<String,Object>();
        value.put("quantity", dbQuantity - quantity.doubleValue());

        //更新库存
        Integer inventoryId = MapUtils.getInteger(map, "inventoryId");
        entityService.updateById(MzfEntity.INVENTORY,inventoryId.toString(),value,user);

        value.clear();
//        String fRemark = "剩余数量：["+(new DecimalFormat(".###").format(dbQuantity - quantity.doubleValue()))+"]";
		RawmaterialType type = MzfEnum.RawmaterialType.valueOf(MapUtils.getString(map,"type"));
		if (type == MzfEnum.RawmaterialType.gravel) {
			if (weight == null) {
				throw new BusinessException("未指定碎石出库重量");
			}
            Double dbWeight = MapUtils.getDouble(map,"weight",0d);

            if(dbWeight < weight.doubleValue() ){
                throw new BusinessException("碎石库存重量不足：为["+dbWeight+"]");
            }

            value.put("weight", dbWeight - weight.doubleValue());
//            fRemark = fRemark + ";剩余重量：["+(new BigDecimal(dbWeight).subtract(weight).round(new MathContext(4)))+"]|"+weight.round(new MathContext(3));
		}
        value.put("cost", dbCost - outCost);
        String rid = MapUtils.getString(map,"id");
        entityService.updateById(MzfEntity.RAWMATERIAL,rid,value,user);

        //记录出库
        inventoryService.createFlowOnQuantity(bizType, inventoryId, quantity, MzfEnum.InventoryType.delivery, outCost, remark, user);

		//记录操作日志
		businessLogService.log("原料库强制出库", "库存编号为：" + inventoryId, user);
	}

	public void lockDiamond(Integer[] rawmaterialId, String remark, IUser user) throws BusinessException {
		rawmaterialService.lockDiamond(rawmaterialId, remark, user);
	}

	public void freeDiamond(Integer[] rawmaterialId, String remark, IUser user) throws BusinessException {
		rawmaterialService.freeDiamond(rawmaterialId, remark, user);
	}

	public void lock(Map<Integer, BigDecimal> rawmaterialIdQuantityMap, IUser user) throws BusinessException {
		if (MapUtils.isEmpty(rawmaterialIdQuantityMap)) {
			return;
		}

		Map<Integer, BigDecimal> inventoryQuantityMap = convertToInventoryQuantityMap(rawmaterialIdQuantityMap, user);
        for (Entry<Integer, BigDecimal> en : inventoryQuantityMap.entrySet()) {
            int inventoryId = en.getKey();
            BigDecimal lockedQuantity = en.getValue();
            inventoryService.lock(inventoryId, lockedQuantity.doubleValue(), user);
        }
	}

	public void free(Map<Integer, BigDecimal> rawmaterialIdQuantityMap, IUser user) throws BusinessException {
		if (MapUtils.isEmpty(rawmaterialIdQuantityMap)) {
			return;
		}

		Map<Integer, BigDecimal> inventoryIdQuantityMap = convertToInventoryQuantityMap(rawmaterialIdQuantityMap, user);
        for (Entry<Integer, BigDecimal> en : inventoryIdQuantityMap.entrySet()) {
            int inventoryId = en.getKey();
            BigDecimal lockedQuantity = en.getValue();
            lockedQuantity = lockedQuantity.multiply(new BigDecimal(-1));
            inventoryService.lock(inventoryId, lockedQuantity.doubleValue(), user);
        }
	}

	public List<Map<String, Object>> list(Integer[] rawmaterialIds, int orgId, IUser user) throws BusinessException {
		EntityMetadata metadata = metadataProvider.getEntityMetadata(MzfEntity.RAWMATERIAL_INVENTORY_VIEW);
		Map<String, Object> where = new HashMap<String, Object>();
		where.put("id", rawmaterialIds);
		where.put("orgId", orgId);
        return entityService.list(metadata, where, null, user.asSystem());
	}

	public Map<String, Object> getInventory(int rawmaterialId, int orgId, IUser user) throws BusinessException {
		List<Map<String, Object>> inventoryList = list(new Integer[]{rawmaterialId}, orgId, user);
		if (CollectionUtils.isEmpty(inventoryList)) {
            return null;
			//throw new BusinessException("库存中未找到该原料[" + rawmaterialId + "]");
		} else if (inventoryList.size() > 1) {
			throw new BusinessException("库存中找到多件原料[" + rawmaterialId + "]");
		}

        return inventoryList.get(0);
	}

	/**
	 * 传入的参数中的key值为原料Id，将其对应成库存Id
     *
	 * @param rawmaterialIdQuantityMap	key:存放rawmaterialId
	 * @return		key:存放inventoryId
	 * @throws BusinessException
	 */
	private Map<Integer, BigDecimal> convertToInventoryQuantityMap(Map<Integer, BigDecimal> rawmaterialIdQuantityMap, IUser user) throws BusinessException {
		Integer[] rawmaterialId = new ArrayList<Integer>(rawmaterialIdQuantityMap.keySet()).toArray(new Integer[new ArrayList<Integer>(rawmaterialIdQuantityMap.keySet()).size()]);
		Map<String, Object> where = new HashMap<String, Object>();
		where.put("orgId", user.getOrgId());
		where.put("targetType", TargetType.rawmaterial);
		where.put("targetId", rawmaterialId);
		EntityMetadata metadata = inventoryService.getEntityMetadataOfInventory();
		List<Map<String, Object>> list = entityService.list(metadata, where, null, user.asSystem());
		Map<Integer, BigDecimal> inventoryQuantityMap = new HashMap<Integer, BigDecimal>();
		for (Map<String, Object> dbInventory : list) {
			Integer inventoryId = MapUtils.getInteger(dbInventory, metadata.getPkCode());
			Integer rawmaterialIdTemp = MapUtils.getInteger(dbInventory, "targetId");
			BigDecimal lockedQuantity = rawmaterialIdQuantityMap.get(rawmaterialIdTemp);
			if (inventoryId == null || lockedQuantity == null) {
				throw new BusinessException("获取参数错误");
			}

			inventoryQuantityMap.put(inventoryId, lockedQuantity);
		}

		return inventoryQuantityMap;
	}
}


