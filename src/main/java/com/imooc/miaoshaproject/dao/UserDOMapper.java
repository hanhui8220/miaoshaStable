package com.imooc.miaoshaproject.dao;

import com.imooc.miaoshaproject.dataobject.UserDO;

public interface UserDOMapper {

    int deleteByPrimaryKey(Integer id);


    int insert(UserDO record);


    int insertSelective(UserDO record);


    UserDO selectByPrimaryKey(Integer id);

    UserDO selectByTelphone(String telphone);

    int updateByPrimaryKeySelective(UserDO record);


    int updateByPrimaryKey(UserDO record);
}