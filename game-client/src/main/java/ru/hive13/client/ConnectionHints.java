package ru.hive13.client;
import java.net.*;
import java.util.*;
public final class ConnectionHints {
    private ConnectionHints(){}
    public static List<String> forHost(String bind,int port){
        if(!bind.equals("0.0.0.0")&&!bind.equals("::"))return List.of(bind+":"+port);
        var addresses=new TreeSet<String>();try{
            for(var adapter:Collections.list(NetworkInterface.getNetworkInterfaces()))if(adapter.isUp()&&!adapter.isLoopback())for(var address:Collections.list(adapter.getInetAddresses()))if(address instanceof Inet4Address&&!address.isLoopbackAddress()&&!address.isLinkLocalAddress())addresses.add(address.getHostAddress()+":"+port);
        }catch(SocketException ignored){}
        return List.copyOf(addresses);
    }
    public static String invitation(List<String> addresses,String role){return "HIVE: PROTOCOL 13\nАдреса хоста (выберите общий LAN или Radmin VPN):\n"+String.join("\n",addresses)+"\nЗанятый персонаж: "+role+"\nВыберите другого персонажа. Пароль сессии, если он задан, хост передаёт отдельно.";}
}
