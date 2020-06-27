package com.mn.springboot.controller;

import com.mn.springboot.utils.Neo4jUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
public class Test02Controller {

    @Autowired
    private Neo4jUtil neo4jUtil;

    /**
     * 查询属性name为aa的节点
     * cql：  match (n) where n.name ="aa" return n
     * res:
     * {
     *   "identity": 9302,
     *   "labels": [
     *     "user"
     *   ],
     *   "properties": {
     *     "name": "aa"
     *   }
     * }
     * @return
     */
    @GetMapping("execCypher01")
    public Map<String, Object> execCypher01(){
        Map<String, Object> retMap = new HashMap<>();
        //cql语句
        String cql = "match (m:Person{name: \"章子怡\"}) return m";
        cql = "match (n) where n.name =\"aa\" return n";
        // String rSql = String.format("MATCH (n:`%s`) -[r]-(m)  delete r", domain);
        String label = ""; //
        String attrName = "name"; //属性名称
        String attrValue = "\"" + "aa" + "\""; //属性值
        String rSql = String.format("MATCH (n) where n.%s =%s return n", attrName,attrValue);
        System.out.println("======rSql======"+rSql);
        Set<Map<String ,Object>> nodeList = new HashSet<>();
        neo4jUtil.getList(rSql,nodeList);
        retMap.put("nodeList",nodeList);
        return retMap;
    }

    // match (n)-[edge]-(n) return n,m,edge
    @PostMapping("execCypher02")
    public Map<String, Object> execCypher02(String cypher){
        Map<String, Object> retMap = new HashMap<>();
        //cql语句
        cypher = "match (n)-[r:friend]-(b) return n,r,b";

        String label = ""; //
        String attrName = "name"; //属性名称
        String attrValue = "\"" + "aa" + "\""; //属性值
        String rSql = cypher;
        System.out.println("======rSql======"+rSql);
        Set<Map<String ,Object>> nodeList = new HashSet<>();
        neo4jUtil.getList(rSql,nodeList);
        retMap.put("nodeList",nodeList);
        return retMap;
    }

}
