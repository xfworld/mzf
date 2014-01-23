package com.zonrong.vendorSale.service;

import com.zonrong.basics.product.service.ProductService;
import com.zonrong.common.utils.MzfEntity;
import com.zonrong.common.utils.MzfEnum;
import com.zonrong.core.exception.BusinessException;
import com.zonrong.core.security.IUser;
import com.zonrong.entity.code.EntityCode;
import com.zonrong.entity.service.EntityService;
import com.zonrong.inventory.service.ProductInventoryService;
import com.zonrong.inventory.service.RawmaterialInventoryService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Alex
 * Date: 13-12-29
 * Time: 下午6:01
 */
@Service
public class VendorSaleService {
    private Logger logger = Logger.getLogger(VendorSaleService.class);

    @Resource
    private EntityService entityService;
    @Resource
    private ProductInventoryService productInventoryService;
    @Resource
    private RawmaterialInventoryService rawmaterialInventoryService;

    public void createProductOrder(Map<String,Object> order,List details,IUser user) throws BusinessException {
        createOrder(order,"product",user);

        String num = MapUtils.getString(order,"num");
        String orderId = MapUtils.getString(order,"id");

        for (Object detail : details) {
            String id = ObjectUtils.toString(detail);
            if(StringUtils.isBlank(id)){
                throw new BusinessException("商品ID不能为空");
            }

            //商品出库
            productInventoryService.deliveryByProductId(MzfEnum.BizType.vendorSell,
                    Integer.parseInt(id),"销售单号：["+num+"]", MzfEnum.InventoryStatus.onStorage,user);

            //保存销售单明细
            Map<String,Object> values = entityService.getById(MzfEntity.PRODUCT,id,user);
            values.put("orderId",orderId);
            entityService.create(new EntityCode("vendorSaleProductDetail"), values, user);

            //更新商品状态为“已售”
            values.clear();
            values.put("status", ProductService.ProductStatus.selled);
            entityService.updateById(new EntityCode("product"),id,values,user);
        }
    }

    public void createDiamondOrder(Map<String,Object> order,List details,IUser user) throws BusinessException {
        createOrder(order, "diamond", user);

        String num = MapUtils.getString(order,"num");
        String orderId = MapUtils.getString(order, "id");

        for (Object detail : details) {
            String id = ObjectUtils.toString(detail);
            if(StringUtils.isBlank(id)){
                throw new BusinessException("裸石ID不能为空");
            }

            //裸石
            rawmaterialInventoryService.deliveryDiamondByRawmaterialId(MzfEnum.BizType.vendorSell,
                    Integer.parseInt(id),"销售单号：["+num+"]",user);

            //保存销售单明细
            Map<String,Object> values = entityService.getById(MzfEntity.RAWMATERIAL,id,user);
            values.put("orderId",orderId);
            entityService.create(new EntityCode("vendorSaleDiamondDetail"), values, user);//在实体定义中进行字段对应

            //更新裸石状态为“已售”
            values.clear();
            values.put("status", MzfEnum.RawmaterialStatus.sold);
            entityService.updateById(MzfEntity.RAWMATERIAL,id,values,user);
        }
    }

    public void createGoldOrder(Map<String,Object> order,List details,IUser user) throws BusinessException {
        createOrder(order, "gold", user);

        String num = MapUtils.getString(order,"num");
        String orderId = MapUtils.getString(order,"id");

        for(Object detail:details){
            Map<String,Object> d = (Map<String, Object>) detail;
            String id = MapUtils.getString(d,"targetId");
            if(StringUtils.isBlank(id)){
                throw new BusinessException("原料Id为空");
            }
            String inventoryId = MapUtils.getString(d,"inventoryId");
            if(StringUtils.isBlank(inventoryId)){
                throw new BusinessException("库存ID为空");
            }
            float quantity = MapUtils.getFloatValue(d,"quantity");
            if(quantity<=0){
                throw new BusinessException("销售数量为空");
            }

            //保存销售单明细
            Map<String,Object> values = entityService.getById(MzfEntity.RAWMATERIAL,id,user);
            values.put("orderId",orderId);
            entityService.create(new EntityCode("vendorSaleGoldDetail"), values, user);//在实体定义中进行字段对应

            //金料出库
            deliveryGold(inventoryId,quantity,num,user);
        }
    }

    public void createSecondGoldOrder(Map<String,Object> order,List details,IUser user) throws BusinessException {
        createOrder(order, "secondGold", user);

        String num = MapUtils.getString(order,"num");
        String orderId = MapUtils.getString(order,"id");

        for(Object detail:details){
            Map<String,Object> d = (Map<String, Object>) detail;
            String id = MapUtils.getString(d,"targetId");
            if(StringUtils.isBlank(id)){
                throw new BusinessException("原料Id为空");
            }
            String inventoryId = MapUtils.getString(d,"inventoryId");
            if(StringUtils.isBlank(inventoryId)){
                throw new BusinessException("库存ID为空");
            }
            float quantity = MapUtils.getFloatValue(d,"quantity");
            if(quantity<=0){
                throw new BusinessException("销售数量为空");
            }

            //保存销售单明细
            Map<String,Object> values = entityService.getById(MzfEntity.RAWMATERIAL,id,user);
            values.put("orderId",orderId);
            entityService.create(new EntityCode("vendorSaleSecondGoldDetail"), values, user);//在实体定义中进行字段对应

            //金料出库
            deliverySecondGold(inventoryId, quantity, num, user);
        }
    }

    public void cancelProductOrder(String id,IUser user) throws BusinessException {
        Map<String,Object> where = new HashMap<String, Object>();
        where.put("orderId",id);
        List<Map<String,Object>> details = entityService.list(new EntityCode("vendorSaleDetail"),where,null,user);
        for(Map<String,Object> detail:details){
            String targetId = MapUtils.getString(detail,"targetId");
        }
    }

    private void createOrder(Map<String,Object> order,String type,IUser user) throws BusinessException {
        checkOrderValue(order);

        String num = "VS"+new SimpleDateFormat("yyMMddHHmmss").format(new Date());
        order.put("type",type);
        order.put("num",num);
        order.put("cdate",null);
        order.put("cuser",null);

        String orderId = entityService.create(new EntityCode("vendorSale"),order,user);
        order.put("id",orderId);
    }

    private void checkOrderValue(Map<String,Object> order) throws BusinessException {
        final String vendorId = MapUtils.getString(order, "vendorId");
        if(StringUtils.isBlank(vendorId)){
            throw new BusinessException("供应商ID不能为空");
        }
        final String vendorOrderNum = MapUtils.getString(order,"vendorOrderNum");
        if(StringUtils.isBlank(vendorOrderNum)){
            throw new BusinessException("供应商单号不能为空");
        }
        final float totalAmount = MapUtils.getFloatValue(order,"totalAmount",0);
        if(totalAmount<=0){
            throw new BusinessException("总金额不能为空");
        }
    }

    //旧金出库
    private void deliverySecondGold(String inventoryId,float quantity,String num,IUser user)throws BusinessException{
        //获取库存记录
        Map<String,Object> values = new HashMap<String,Object>();
        values.put("inventoryId",inventoryId);
        List<Map<String,Object>> inventorys = entityService.list(MzfEntity.SECOND_GOLD_INVENTORY_VIEW,values,null,user);
        if(CollectionUtils.isEmpty(inventorys)){
            throw new BusinessException("金料出库时，库存记录为空");
        }

        Map<String,Object> inventory = inventorys.get(0);
        //计算出库成本
        float dbQuantity = MapUtils.getFloatValue(inventory,"quantity",0);
        if(dbQuantity < quantity){
            throw new BusinessException("金料出库时，库存数量不足");
        }

        //todo:成本的计算需要进一步修改
//        float dbCost = MapUtils.getFloatValue(inventory,"cost");
//        float cost = dbCost*quantity/dbQuantity;

        //更新库存记录
        values.clear();
        values.put("quantity",dbQuantity - quantity);
//        values.put("cost",dbCost - cost);
        entityService.updateById(MzfEntity.INVENTORY,inventoryId,values,user);

        //创建出库记录
        values.clear();
        values.put("bizType", MzfEnum.BizType.vendorSell);
        values.put("orgId", MapUtils.getShortValue(inventory,"orgId"));
        values.put("storageType", MzfEnum.StorageType.rawmaterial_gold);
        values.put("type", MzfEnum.InventoryType.delivery);
        values.put("targetType", MzfEnum.TargetType.rawmaterial);
        values.put("targetId", MapUtils.getString(inventory,"id"));
        values.put("remark", "销售单号："+num);
        values.put("quantity", quantity);
        values.put("cuserId", user.getId());
        values.put("cdate", null);
//        values.put("cost",cost);

        entityService.create(MzfEntity.INVENTORY_FLOW,values,user);
    }

    //金料出库
    private void deliveryGold(String inventoryId,float quantity,String num,IUser user) throws BusinessException {
        //获取库存记录
        Map<String,Object> values = new HashMap<String,Object>();
        values.put("inventoryId",inventoryId);
        List<Map<String,Object>> inventorys = entityService.list(MzfEntity.RAWMATERIAL_INVENTORY_VIEW,values,null,user);
        if(CollectionUtils.isEmpty(inventorys)){
            throw new BusinessException("金料出库时，库存记录为空");
        }

        Map<String,Object> inventory = inventorys.get(0);
        //计算出库成本
        float dbQuantity = MapUtils.getFloatValue(inventory,"quantity",0);
        if(dbQuantity < quantity){
            throw new BusinessException("金料出库时，库存数量不足");
        }

        float dbCost = MapUtils.getFloatValue(inventory,"cost");
        float cost = dbCost*quantity/dbQuantity;

        //更新库存记录
        values.clear();
        values.put("quantity",dbQuantity - quantity);
        values.put("cost",dbCost - cost);
        entityService.updateById(MzfEntity.INVENTORY,inventoryId,values,user);

        //创建出库记录
        values.clear();
        values.put("bizType", MzfEnum.BizType.vendorSell);
        values.put("orgId", MapUtils.getShortValue(inventory,"orgId"));
        values.put("storageType", MzfEnum.StorageType.rawmaterial_gold);
        values.put("type", MzfEnum.InventoryType.delivery);
        values.put("targetType", MzfEnum.TargetType.rawmaterial);
        values.put("targetId", MapUtils.getString(inventory,"id"));
        values.put("remark", "销售单号："+num);
        values.put("quantity", quantity);
        values.put("cuserId", user.getId());
        values.put("cdate", null);
        values.put("cost",cost);

        entityService.create(MzfEntity.INVENTORY_FLOW,values,user);
    }

    //配件出库
    private void deliveryPart(String inventoryId,int quantity){

    }

    //碎石出库
    private void deliveryGravel(String inventoryId,int quantity,float weight){

    }
}