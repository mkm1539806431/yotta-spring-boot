package com.xjtu.spider_topic.spiders.wikicn;

import app.Config;
import assemble.bean.AssembleFragmentFuzhu;
import domainTopic.bean.Topic;
import facet.bean.FacetRelation;
import facet.bean.FacetSimple;
import org.jsoup.nodes.Document;
import utils.JsoupDao;
import utils.Log;
import utils.SpiderUtils;

import java.util.List;

/**
 * 构建领域知识森林数据
 *  
 * @author 郑元浩 
 * @date 2016年11月29日
 */
public class FragmentCrawler {
	
	/**
	 * 将领域术语网页内容按照分面存储到数据库
	 * @param domainName 课程名
	 * @throws Exception
	 */
	public static void storeKGByDomainName(String domainName) throws Exception{
		
		/**
		 * 读取数据库表格domain_topic，得到领域术语
		 */
//		String domain = "数据结构";
		List<Topic> topicList = MysqlReadWriteDAO.getDomainTopic(domainName);
		for(int i = 0; i < topicList.size(); i++){
			Topic topic = topicList.get(i);
			int topicID = topic.getTopicID();
			String topicName = topic.getTopicName();
			String topicUrl = topic.getTopicUrl();

			/**
			 * 判断数据是否已经存在
			 */
			Boolean existFacet = MysqlReadWriteDAO.judgeByClassAndTopic(Config.FACET_TABLE, domainName, topicName);
			Boolean existFacetRelation = MysqlReadWriteDAO.judgeByClassAndTopic(Config.FACET_RELATION_TABLE, domainName, topicName);
			Boolean existAssembleFragment = MysqlReadWriteDAO.judgeByClassAndTopic(Config.ASSEMBLE_FRAGMENT_TABLE, domainName, topicName);

			/**
			 * 判断该主题的信息是不是在所有表格中已经存在
			 * 只要有一个不存在就需要再次爬取（再次模拟加载浏览器）
			 */
			if(!existFacet || !existFacetRelation || !existAssembleFragment){

				/**
				 * selenium解析网页
				 */
				String topicHtml = SpiderUtils.seleniumWikiCN(topicUrl);
				Document doc = JsoupDao.parseHtmlText(topicHtml);

				// 获取并存储所有分面信息Facet
				List<FacetSimple> facetSimpleList = FragmentCrawlerDAO.getFacet(doc);
				if(!existFacet){
					MysqlReadWriteDAO.storeFacet(domainName, topicID, topicName, facetSimpleList);
					Log.log(domainName + "，" + topicName + "：分面爬取完毕");
				} else {
					Log.log(domainName + "， " + topicName + "：分面已经爬取");
				}

				// 获取并存储各级分面之间的关系FacetRelation
				List<FacetRelation> facetRelationList = FragmentCrawlerDAO.getFacetRelation(doc);
				if(!existFacetRelation){
					MysqlReadWriteDAO.storeFacetRelation(domainName, topicID, topicName, facetRelationList);
					Log.log(domainName + "，" + topicName + "：分面关系爬取完毕");
				} else {
					Log.log(domainName + "，" + topicName + "：分面关系已经爬取");
				}

				// 获得Assemble_fragment：一级分面下如果有二级分面，那么一级分面应该没有碎片文本
				List<AssembleFragmentFuzhu> assembleFragmentList = FragmentCrawlerDAO.getFragmentUseful(domainName, topicName, doc);
				if(!existAssembleFragment){
					MysqlReadWriteDAO.storeFragment(domainName, topicID, topicName, topicUrl, assembleFragmentList);
					Log.log(domainName + "，" + topicName + "：碎片爬取完毕");
				} else {
					Log.log(domainName + "，" + topicName + "：碎片已经爬取");
				}
			} else {
				Log.log(domainName + "，" + topicName + "分面、分面关系、碎片表中数据已经存在");
			}
		}

		/**
		 * 保存主题间的上下位关系
		 */
		FragmentCrawlerDAO.getDependenceByClassName(domainName, false);

		/**
		 * 生成gephi文件
		 */
		FragmentCrawlerDAO.getGexfByClassName(domainName);
	}

}
