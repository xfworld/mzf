package com.zonrong.transfer.secondGold.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections.MapUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.zonrong.core.exception.BusinessException;
import com.zonrong.core.templete.HttpTemplete;
import com.zonrong.core.templete.OperateTemplete;
import com.zonrong.transfer.secondGold.service.TransferSecondGoldService;

/**
 * date: 2010-11-24
 *
 * version: 1.0
 * commonts: ......
 */
@Controller
@RequestMapping(value="/code/transfer/secondGold")
public class TransferSecondGoldController {
	private Logger logger = Logger.getLogger(this.getClass());
	
	@Resource
	private TransferSecondGoldService transferSecondGoldService;
	
	@RequestMapping(value="/transfer", method = RequestMethod.POST)
	@ResponseBody
	public Map create(@RequestBody final Map<String, Object> transfer, HttpServletRequest request) {
		OperateTemplete templete = new HttpTemplete(request) {
			protected void doSomething() throws BusinessException {
				Integer secondGoldId = MapUtils.getInteger(transfer, "secondGoldId");
				String quantity = MapUtils.getString(transfer, "quantity", new Integer(0).toString());
				if (quantity == null) {
					throw new BusinessException("未指定调拨量");
				}
				transferSecondGoldService.transfer(secondGoldId, new BigDecimal(quantity), transfer, this.getUser());
			}			
		};
		return templete.operate();			
	}	
	
	@RequestMapping(value = "/send", method = RequestMethod.PUT)
	@ResponseBody
	public Map send(@RequestBody final Integer[] transferIds, HttpServletRequest request) {
		final Map<String, Object> dispatch = new HashMap<String, Object>();
		dispatch.put("targetOrgId", request.getParameter("targetOrgId"));
		dispatch.put("remark", request.getParameter("remark"));
		OperateTemplete templete = new HttpTemplete(request) {
			protected void doSomething() throws BusinessException {
				int dispatchId = transferSecondGoldService.send(transferIds, dispatch, this.getUser());
				this.put("dispatchId", dispatchId);
			}			
		};
		return templete.operate();			
	}
	
	@RequestMapping(value = "/receive/{id}", method = RequestMethod.PUT)
	@ResponseBody
	public Map receive(@PathVariable final int id, @RequestBody final Map<String, Object> receive, HttpServletRequest request) {
		OperateTemplete templete = new HttpTemplete(request) {
			protected void doSomething() throws BusinessException {
				transferSecondGoldService.receive(id, receive, this.getUser());
			}			
		};
		return templete.operate();			
	}
	
	@RequestMapping(value = "/printData", method = RequestMethod.GET)
	@ResponseBody
	public Map printData(@RequestParam final Map<String, Object> param,  HttpServletRequest request) {
		OperateTemplete templete = new HttpTemplete(request) {
			protected void doSomething() throws BusinessException {
				String startDate = MapUtils.getString(param, "startDate", "");
				String endDate = MapUtils.getString(param, "endDate", "");
				Map<String, Object> data = transferSecondGoldService.getPrintData(startDate, endDate, this.getUser());
				this.put("data", data);
			}			
		};
		return templete.operate();			
	}	
}


