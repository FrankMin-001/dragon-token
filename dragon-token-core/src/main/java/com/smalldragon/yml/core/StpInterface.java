package com.smalldragon.yml.core;

import java.util.List;

public interface StpInterface {

    List<String> getPermissionList(String userId);

    List<String> getRoleList(String userId);

}
