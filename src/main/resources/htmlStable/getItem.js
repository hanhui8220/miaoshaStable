// phantom  爬取商品详情页 脚本
var page = require("webpage").create();
var fs = require("fs");
page.open("http://192.168.81.138/miaosha/getitem.html?id=6",function (status) {
    console.log("----------"+status);
    setTimeout(function () {
        fs.write("getitem-phantom.html",page.content(),"w");\
        phantom.exit();
    },1000);
});