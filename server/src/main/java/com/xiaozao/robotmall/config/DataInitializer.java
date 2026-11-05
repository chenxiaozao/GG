package com.xiaozao.robotmall.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.xiaozao.robotmall.entity.*;
import com.xiaozao.robotmall.repository.*;
import com.xiaozao.robotmall.service.CatalogMediaService;

@Component
public class DataInitializer implements CommandLineRunner {
    @org.springframework.beans.factory.annotation.Value("${app.demo-users:true}")
    private boolean demoUsers;
    @org.springframework.beans.factory.annotation.Value("${app.bootstrap-admin-password:}")
    private String bootstrapPassword;
    private final UserRepository users;
    private final CategoryRepository cats;
    private final BrandRepository brands;
    private final ProductRepository products;
    private final CompatibilityRepository compat;
    private final PasswordEncoder encoder;
    private final CatalogMediaService media;

    private record ProductSeed(
            String name, String subtitle, String price, String marketPrice, String image,
            String category, String brand, String type, int endurance, double payload,
            String level, String description, String params, int sales, double rating,
            int stock, String skuOptions, String sourceUrl, String warranty, String shippingNote) {
    }

    public DataInitializer(UserRepository users, CategoryRepository cats,
            BrandRepository brands, ProductRepository products,
            CompatibilityRepository compat, PasswordEncoder encoder, CatalogMediaService media) {
        this.users = users;
        this.cats = cats;
        this.brands = brands;
        this.products = products;
        this.compat = compat;
        this.encoder = encoder;
        this.media = media;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        Map<String, Long> categoryIds = seedCategories();
        Map<String, Long> brandIds = seedBrands();
        seedProducts(categoryIds, brandIds);
        seedCompatibility();
    }

    private void seedUsers() {
        if (!demoUsers) {
            for (User existing : users.findAll()) {
                if (encoder.matches("admin123", existing.password) || encoder.matches("123456", existing.password)) {
                    throw new IllegalStateException("线上模式不能使用演示密码，请使用独立数据库或先修改演示账号密码");
                }
            }
            if (users.findByUsername("admin").isEmpty()) {
                if (bootstrapPassword == null || bootstrapPassword.length() < 12)
                    throw new IllegalStateException("首次线上启动需设置至少 12 位 BOOTSTRAP_ADMIN_PASSWORD");
                User admin = new User();
                admin.username = "admin";
                admin.password = encoder.encode(bootstrapPassword);
                admin.nickname = "管理员";
                admin.role = "ADMIN";
                admin.createTime = LocalDateTime.now();
                users.save(admin);
            }
            return;
        }
        if (users.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.username = "admin";
            admin.password = encoder.encode("admin123");
            admin.nickname = "运营管理员";
            admin.role = "ADMIN";
            admin.enabled = true;
            admin.createTime = LocalDateTime.now();
            users.save(admin);
        }
        if (users.findByUsername("user").isEmpty()) {
            User user = new User();
            user.username = "user";
            user.password = encoder.encode("123456");
            user.nickname = "演示用户";
            user.role = "USER";
            user.enabled = true;
            user.createTime = LocalDateTime.now();
            users.save(user);
        }
    }

    private Map<String, Long> seedCategories() {
        String[] names = {"地面清洁", "擦窗机器人", "庭院与泳池", "陪伴娱乐", "航拍无人机",
                "教育编程", "四足机器人", "人形机器人", "商用服务", "机器人配件"};
        Map<String, String> aliases = Map.of(
                "地面清洁", "家用清洁",
                "航拍无人机", "户外航拍");
        Map<String, Long> ids = new LinkedHashMap<>();
        List<Category> existing = cats.findAll();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            Category category = existing.stream().filter(x -> name.equals(x.name)).findFirst().orElse(null);
            if (category == null && aliases.containsKey(name)) {
                String oldName = aliases.get(name);
                category = existing.stream().filter(x -> oldName.equals(x.name)).findFirst().orElse(null);
            }
            if (category == null) category = new Category();
            category.name = name;
            category.sortNo = i;
            category.enabled = true;
            category = cats.save(category);
            ids.put(name, category.id);
        }
        ids.put("家用清洁", ids.get("地面清洁"));
        return ids;
    }

    private Map<String, Long> seedBrands() {
        String[][] data = {
            {"宇树科技", "四足与人形机器人研发品牌"},
            {"石头科技", "智能清洁机器人品牌"},
            {"科沃斯", "家庭服务机器人品牌"},
            {"大疆", "航拍与教育机器人品牌"},
            {"优必选", "人形与商用服务机器人品牌"},
            {"云鲸智能", "家用清洁机器人品牌"},
            {"追觅科技", "智能清洁设备品牌"},
            {"Autel Robotics", "专业航拍无人机品牌"},
            {"普渡机器人", "商用配送与服务机器人品牌"},
            {"小米", "智能家居与清洁设备品牌"},
            {"HOBOT", "擦窗与家用清洁机器人品牌"},
            {"Mammotion", "无边界智能割草机器人品牌"},
            {"Segway Navimow", "智能庭院机器人品牌"},
            {"Beatbot", "智能泳池清洁机器人品牌"},
            {"Aiper", "无线泳池清洁机器人品牌"},
            {"Sony", "智能娱乐与陪伴机器人品牌"},
            {"KEYi Tech", "家用 AI 陪伴机器人品牌"},
            {"Enabot", "家庭陪伴与看护机器人品牌"},
            {"Miko", "儿童 AI 学习陪伴机器人品牌"},
            {"Potensic", "消费级航拍无人机品牌"},
            {"Makeblock", "创客与编程教育机器人品牌"},
            {"Sphero", "互动编程教育机器人品牌"}
        };
        Map<String, Long> ids = new LinkedHashMap<>();
        Map<String, Brand> existing = brands.findAll().stream()
                .collect(Collectors.toMap(x -> x.name, Function.identity(), (a, b) -> a));
        for (String[] row : data) {
            Brand brand = existing.getOrDefault(row[0], new Brand());
            brand.name = row[0];
            brand.description = row[1];
            brand.enabled = true;
            brand = brands.save(brand);
            ids.put(row[0], brand.id);
        }
        return ids;
    }

    private void seedProducts(Map<String, Long> c, Map<String, Long> b) {
        String homeWarranty = "整机 2 年保修，耗材及电池按品牌政策执行";
        String droneWarranty = "官方保修 1 年，飞行损坏不在标准保修范围内";
        String projectWarranty = "项目交付保修 1 年，含远程技术支持";
        String accessoryWarranty = "7 天质量问题换新，拆封耗材不支持无理由退货";
        String inStock = "现货商品预计 24～48 小时内发出";
        String projectShipping = "项目制商品需确认配置，预计 15～30 个工作日交付";

        List<ProductSeed> catalog = new ArrayList<>(List.of(
            new ProductSeed("石头 G20S Ultra", "全能扫拖机器人", "5999", "6999", "/products/real/roborock-g20s.jpeg", "家用清洁", "石头科技", "ROBOT", 240, 0, "HIGH", "支持自动集尘、80℃热水洗拖布、动态机械臂贴边清洁与智能避障，适合大户型和养宠家庭。", "{\"吸力\":\"10000Pa\",\"导航方式\":\"LDS激光导航\",\"避障系统\":\"双线结构光+RGB\",\"基站能力\":\"集尘/洗拖布/烘干/补水\",\"机身高度\":\"7.98cm\",\"拖布类型\":\"双区声波震动\"}", 1260, 4.8, 120, "水箱版,上下水版", "https://www.roborocktw.com/", homeWarranty, inStock),
            new ProductSeed("石头 P20 Pro", "双机械臂旗舰扫拖机器人", "4499", "5499", "/products/real/roborock-g20s.jpeg", "家用清洁", "石头科技", "ROBOT", 210, 0, "HIGH", "双机械臂贴边与低矮空间清洁方案，兼顾毛发防缠绕和基站自清洁。", "{\"吸力\":\"18500Pa\",\"导航方式\":\"LDS激光导航\",\"避障系统\":\"结构光避障\",\"基站能力\":\"热水洗/烘干/集尘\",\"拖布类型\":\"双旋转拖布\",\"越障能力\":\"4cm\"}", 830, 4.7, 88, "水箱版,上下水版", "https://www.roborock.com/", homeWarranty, inStock),
            new ProductSeed("石头 Qrevo S", "高性价比全能基站扫拖机器人", "2999", "3599", "/products/real/roborock-g20s.jpeg", "家用清洁", "石头科技", "ROBOT", 180, 0, "MEDIUM", "面向日常清洁的全能基站机型，支持自动集尘、拖布清洗和地图分区。", "{\"吸力\":\"7000Pa\",\"导航方式\":\"LDS激光导航\",\"避障系统\":\"红外避障\",\"基站能力\":\"洗拖布/烘干/集尘\",\"拖布类型\":\"双旋转拖布\",\"尘袋容量\":\"2.7L\"}", 1060, 4.6, 132, "标准版,自动上下水版", "https://www.roborock.com/", homeWarranty, inStock),
            new ProductSeed("科沃斯 T30 Pro", "灵隙恒贴边扫拖机器人", "4699", "5299", "/products/real/ecovacs-t30-pro.jpg", "家用清洁", "科沃斯", "ROBOT", 260, 0, "HIGH", "支持 70℃热水洗拖布、灵隙贴边和 ZeroTangle 防缠绕，适合多房间家庭。", "{\"吸力\":\"11000Pa\",\"导航方式\":\"TrueMapping 2.0\",\"避障系统\":\"TrueDetect 3D\",\"基站能力\":\"热水洗/烘干/集尘\",\"拖布类型\":\"恒压旋转\",\"语音助手\":\"YIKO\"}", 980, 4.7, 98, "水箱版,自动上下水版", "https://www.ecovacs.cn/", homeWarranty, inStock),
            new ProductSeed("科沃斯 X5 PRO OMNI", "超薄全嵌入式扫拖机器人", "5299", "6299", "/products/real/ecovacs-t30-pro.jpg", "家用清洁", "科沃斯", "ROBOT", 230, 0, "HIGH", "方形机身加强边角覆盖，超薄设计适合低矮家具，并支持全能基站维护。", "{\"吸力\":\"12800Pa\",\"导航方式\":\"嵌入式激光雷达\",\"避障系统\":\"AIVI 3D\",\"基站能力\":\"集尘/洗烘/补水\",\"机身高度\":\"9.5cm\",\"边角覆盖\":\"方形机身\"}", 510, 4.7, 65, "水箱版,自动上下水版", "https://www.ecovacs.cn/", homeWarranty, inStock),
            new ProductSeed("云鲸 J5", "智能扫拖清洁机器人", "4299", "4999", "/products/real/narwal-freo-x.png", "家用清洁", "云鲸智能", "ROBOT", 220, 0, "HIGH", "支持自动洗拖布、基站自清洁与智能脏污识别，强调拖地体验。", "{\"吸力\":\"15000Pa\",\"导航方式\":\"激光导航\",\"避障系统\":\"双目结构光\",\"基站能力\":\"洗拖布/烘干/补水\",\"拖布压力\":\"动态加压\",\"毛发处理\":\"防缠绕滚刷\"}", 520, 4.6, 72, "水箱版,自动上下水版", "https://www.narwal.com/", homeWarranty, inStock),
            new ProductSeed("云鲸 Freo X Ultra", "免维护扫拖一体机器人", "5499", "6499", "/products/real/narwal-freo-x.png", "家用清洁", "云鲸智能", "ROBOT", 210, 0, "HIGH", "DirtSense 脏污识别、零缠绕滚刷与长周期尘袋设计，降低日常维护频率。", "{\"吸力\":\"8200Pa\",\"导航方式\":\"LiDAR SLAM 4.0\",\"避障系统\":\"三激光避障\",\"基站能力\":\"洗拖布/烘干/补水\",\"尘袋周期\":\"最长7周\",\"毛发处理\":\"零缠绕滚刷\"}", 460, 4.7, 58, "标准版,配件套装版", "https://us.narwal.com/products/narwal-freo-x-ultra-robot-vacuum-mop", homeWarranty, inStock),
            new ProductSeed("追觅 X40 Ultra", "仿生双机械臂扫拖机器人", "4999", "5999", "/products/real/dreame-x40-ultra.png", "家用清洁", "追觅科技", "ROBOT", 198, 0, "HIGH", "边刷和拖布双外扩，支持抬升主刷与 70℃热水洗拖布，适合复杂家庭地面。", "{\"吸力\":\"12000Pa\",\"导航方式\":\"LDS激光导航\",\"避障系统\":\"RGB+结构光\",\"基站能力\":\"热水洗/烘干/集尘\",\"边角清洁\":\"边刷+拖布双外扩\",\"主刷抬升\":\"支持\"}", 760, 4.8, 84, "水箱版,自动上下水版", "https://www.dreametech.com/products/x40-ultra", homeWarranty, inStock),
            new ProductSeed("大疆 Air 3S", "双摄旅行航拍无人机", "6988", "7999", "/products/real/dji-air3s.jpg", "航拍无人机", "大疆", "ROBOT", 45, 0.72, "PRO", "1 英寸主摄与中长焦双摄，支持夜景全向避障和新一代智能返航。", "{\"相机系统\":\"1英寸主摄+70mm中长焦\",\"最高视频\":\"4K/120fps\",\"飞行时间\":\"45分钟\",\"图传距离\":\"20km\",\"避障系统\":\"夜景全向视觉+前视激光雷达\",\"起飞重量\":\"724g\"}", 640, 4.8, 66, "RC-N3套装,畅飞套装RC-N3,畅飞套装RC 2", "https://www.dji.com/air-3s", droneWarranty, inStock),
            new ProductSeed("大疆 Mavic 3 Pro", "三摄旗舰航拍无人机", "13888", "14988", "/products/real/dji-mavic3-pro.webp", "航拍无人机", "大疆", "ROBOT", 43, 0.96, "PRO", "哈苏主摄、70mm 中长焦和 166mm 长焦三摄系统，面向专业内容创作。", "{\"相机系统\":\"4/3哈苏主摄+双长焦\",\"最高视频\":\"5.1K/50fps\",\"飞行时间\":\"43分钟\",\"图传距离\":\"15km\",\"避障系统\":\"全向视觉\",\"起飞重量\":\"958g\"}", 390, 4.9, 32, "DJI RC套装,畅飞套装,大师套装", "https://www.dji.com/mavic-3-pro", droneWarranty, inStock),
            new ProductSeed("大疆 Mini 4 Pro", "轻量全向避障航拍无人机", "4788", "5488", "/products/real/dji-air3s.jpg", "航拍无人机", "大疆", "ROBOT", 34, 0.249, "HIGH", "低于 249g 的轻量机身，支持全向避障、竖拍和智能跟随，适合旅行记录。", "{\"相机系统\":\"1/1.3英寸CMOS\",\"最高视频\":\"4K/100fps\",\"飞行时间\":\"34分钟\",\"图传距离\":\"20km\",\"避障系统\":\"全向视觉\",\"起飞重量\":\"<249g\"}", 1180, 4.8, 110, "RC-N2套装,RC 2套装,畅飞套装", "https://www.dji.com/mini-4-pro", droneWarranty, inStock),
            new ProductSeed("Autel EVO Lite+", "一英寸 6K 航拍无人机", "7599", "8299", "/products/real/autel-evo-lite-plus.png", "航拍无人机", "Autel Robotics", "ROBOT", 40, 0.835, "PRO", "搭载 1 英寸 20MP 相机、可变光圈和 6K 视频能力，适合低照度航拍。", "{\"相机系统\":\"1英寸20MP\",\"最高视频\":\"6K/30fps\",\"飞行时间\":\"40分钟\",\"图传距离\":\"12km\",\"避障系统\":\"前后下三向视觉\",\"起飞重量\":\"835g\"}", 210, 4.6, 28, "标准套装,畅飞套装", "https://shop.autelrobotics.com/products/drones-evo-lite-plus", droneWarranty, inStock),
            new ProductSeed("宇树 Go2 Air", "消费级智能四足机器人", "11999", "12999", "/products/real/unitree-go2.webp", "四足机器人", "宇树科技", "ROBOT", 90, 7.0, "MEDIUM", "面向科技体验和基础教育的四足机器人，支持智能跟随、动态图形动作和 App 控制。", "{\"运行时间\":\"1～2小时\",\"最高速度\":\"2.5m/s\",\"整机重量\":\"约15kg\",\"感知系统\":\"4D激光雷达L1\",\"最大负载\":\"7kg\",\"开发接口\":\"基础接口\"}", 132, 4.7, 24, "Air标准版", "https://www.unitree.com/go2", projectWarranty, projectShipping),
            new ProductSeed("宇树 Go2 Pro", "进阶智能四足机器人", "19999", "22999", "/products/real/unitree-go2.webp", "四足机器人", "宇树科技", "ROBOT", 120, 8.0, "HIGH", "4D 激光雷达与智能跟随，适合高校教学、互动展示和轻量科研开发。", "{\"运行时间\":\"2～4小时\",\"最高速度\":\"3.5m/s\",\"整机重量\":\"约15kg\",\"感知系统\":\"4D激光雷达L1\",\"最大负载\":\"8kg\",\"开发接口\":\"Pro API\"}", 86, 4.9, 36, "Pro标准版,Pro长续航版", "https://www.unitree.com/go2", projectWarranty, projectShipping),
            new ProductSeed("宇树 Go2 EDU", "科研教育四足机器人平台", "69900", "75900", "/products/real/unitree-go2.webp", "四足机器人", "宇树科技", "ROBOT", 180, 10.0, "PRO", "开放 SDK 与 ROS 接口，可选配高算力模块和传感器，适合高校实验室与算法验证。", "{\"运行时间\":\"2～4小时\",\"最高速度\":\"5m/s\",\"整机重量\":\"约15kg\",\"感知系统\":\"4D激光雷达L2\",\"最大负载\":\"10kg\",\"开发接口\":\"SDK/ROS2\"}", 48, 4.9, 12, "EDU基础版,EDU高算力版", "https://www.unitree.com/go2", projectWarranty, projectShipping),
            new ProductSeed("宇树 B2", "工业级四足机器人", "320000", "350000", "/products/real/unitree-b2.webp", "四足机器人", "宇树科技", "ROBOT", 300, 40.0, "PRO", "面向巡检、救援和复杂地形运输的工业四足平台，支持 IP67 防护和模块扩展。", "{\"运行时间\":\"5小时以上\",\"最高速度\":\"6m/s\",\"整机重量\":\"约60kg\",\"防护等级\":\"IP67\",\"动态负载\":\"40kg\",\"越障高度\":\"40cm\"}", 32, 4.9, 8, "标准版,激光雷达巡检版", "https://www.unitree.com/b2", projectWarranty, projectShipping),
            new ProductSeed("宇树 B2-W", "轮足工业巡检机器人", "398000", "428000", "/products/real/unitree-b2.webp", "四足机器人", "宇树科技", "ROBOT", 300, 40.0, "PRO", "轮足融合结构兼顾高速平地移动与复杂地形通过能力，适合大范围工业巡检。", "{\"运行时间\":\"5小时以上\",\"最高速度\":\"6m/s\",\"整机重量\":\"约65kg\",\"防护等级\":\"IP67\",\"动态负载\":\"40kg\",\"移动结构\":\"轮足融合\"}", 18, 4.8, 5, "标准巡检版,自主充电版", "https://www.unitree.com/b2", projectWarranty, projectShipping),
            new ProductSeed("宇树 G1", "人形智能体开发平台", "99000", "109000", "/products/real/unitree-g1.jpg", "人形机器人", "宇树科技", "ROBOT", 120, 2.0, "PRO", "紧凑型人形机器人平台，配备 3D 激光雷达和深度相机，面向具身智能研究。", "{\"身高\":\"约132cm\",\"整机重量\":\"约35kg\",\"续航时间\":\"约2小时\",\"关节自由度\":\"23～43\",\"感知系统\":\"3D激光雷达+深度相机\",\"最高速度\":\"2m/s\"}", 76, 4.8, 10, "G1基础版,G1 EDU版", "https://www.unitree.com/g1", projectWarranty, projectShipping),
            new ProductSeed("宇树 G1 EDU", "高校具身智能科研平台", "299000", "329000", "/products/real/unitree-g1.jpg", "人形机器人", "宇树科技", "ROBOT", 120, 3.0, "PRO", "提供更高算力、灵巧手和开放开发接口，适用于高校科研与算法课程。", "{\"身高\":\"约132cm\",\"整机重量\":\"约35kg\",\"续航时间\":\"约2小时\",\"关节自由度\":\"43\",\"感知系统\":\"3D激光雷达+深度相机\",\"开发接口\":\"SDK/ROS2\"}", 34, 4.9, 6, "EDU U2,EDU U4,EDU U6", "https://www.unitree.com/g1", projectWarranty, projectShipping),
            new ProductSeed("宇树 H1", "全尺寸通用人形机器人", "650000", "720000", "/products/real/unitree-h1.png", "人形机器人", "宇树科技", "ROBOT", 90, 10.0, "PRO", "面向科研与工业验证的全尺寸人形机器人，具备高动态行走和 360° 环境感知能力。", "{\"身高\":\"约180cm\",\"整机重量\":\"约47kg\",\"续航时间\":\"约1.5小时\",\"关节自由度\":\"19+\",\"感知系统\":\"MID360激光雷达+深度相机\",\"最高速度\":\"3.3m/s\"}", 18, 4.8, 5, "H1基础版,H1科研扩展版", "https://www.unitree.com/h1", projectWarranty, projectShipping),
            new ProductSeed("优必选 Cruzr", "商用迎宾与讲解机器人", "128000", "148000", "/products/real/ubtech-cruzr.png", "商用服务", "优必选", "ROBOT", 480, 0, "PRO", "面向展厅、政务和零售场景的迎宾讲解机器人，支持自主导航和多模态交互。", "{\"续航时间\":\"5～8小时\",\"机身高度\":\"约121cm\",\"屏幕尺寸\":\"11.6英寸\",\"导航方式\":\"激光SLAM\",\"交互方式\":\"语音/视觉/触控\",\"充电方式\":\"自主回充\"}", 88, 4.6, 14, "标准接待版,展厅讲解版", "https://www.ubtrobot.com/", projectWarranty, projectShipping),
            new ProductSeed("普渡 KettyBot", "配送迎宾广告机器人", "45900", "52900", "/products/real/pudu-kettybot.png", "商用服务", "普渡机器人", "ROBOT", 480, 30.0, "HIGH", "集配送、迎宾和移动广告于一体，适合餐饮、零售和展会场景。", "{\"续航时间\":\"8小时\",\"最大负载\":\"30kg\",\"屏幕尺寸\":\"18.5英寸\",\"导航方式\":\"视觉+激光SLAM\",\"最小通行宽度\":\"55cm\",\"充电方式\":\"自主回充\"}", 145, 4.7, 22, "标准版,广告运营版", "https://www.pudurobotics.com/", projectWarranty, projectShipping),
            new ProductSeed("普渡 FlashBot", "楼宇配送机器人", "68900", "75900", "/products/real/pudu-flashbot.webp", "商用服务", "普渡机器人", "ROBOT", 720, 30.0, "PRO", "面向酒店和写字楼的封闭舱配送机器人，支持电梯联动、身份验证与自动回充。", "{\"续航时间\":\"12小时\",\"最大负载\":\"30kg\",\"货舱结构\":\"可调多舱\",\"导航方式\":\"VSLAM+激光雷达\",\"楼宇能力\":\"电梯/门禁联动\",\"充电方式\":\"自主回充\"}", 96, 4.8, 16, "双舱版,四舱版", "https://www.pudurobotics.com/", projectWarranty, projectShipping),
            new ProductSeed("大疆 RoboMaster S1", "教育编程机器人", "3499", "4299", "/products/real/dji-robomaster-s1.jpg", "教育编程", "大疆", "ROBOT", 90, 0, "HIGH", "支持 Scratch 与 Python 编程，麦克纳姆轮底盘和视觉识别适合 STEM 教学与竞技体验。", "{\"编程方式\":\"Scratch/Python\",\"传感器数量\":\"31个\",\"移动方式\":\"麦克纳姆轮\",\"图传规格\":\"1080P/30fps\",\"续航时间\":\"约35分钟\",\"组装方式\":\"模块化组装\"}", 720, 4.7, 80, "标准版,畅玩套装", "https://www.dji.com/robomaster-s1", "整机保修 1 年，耗材除外", inStock),
            new ProductSeed("大疆 RoboMaster EP Core", "高校教学与赛事开发平台", "12999", "14999", "/products/real/dji-robomaster-s1.jpg", "教育编程", "大疆", "ROBOT", 100, 0, "PRO", "面向高校课程与机器人竞赛，提供开放 SDK、多机协同和丰富扩展接口。", "{\"编程方式\":\"Scratch/Python/SDK\",\"传感器扩展\":\"红外/距离/视觉\",\"移动方式\":\"麦克纳姆轮\",\"多机协同\":\"支持\",\"续航时间\":\"约40分钟\",\"开发接口\":\"开放SDK\"}", 126, 4.8, 28, "EP Core教育版,多机套装", "https://www.dji.com/robomaster-ep-core", "整机保修 1 年，耗材除外", inStock),
            new ProductSeed("优必选 Alpha Mini", "桌面陪伴编程机器人", "2499", "2999", "/products/real/ubtech-alpha-mini.webp", "教育编程", "优必选", "ROBOT", 120, 0, "MEDIUM", "轻量桌面人形机器人，支持动作编程、表情互动和基础视觉识别。", "{\"编程方式\":\"Blockly/Scratch/Python\",\"关节自由度\":\"14\",\"视觉系统\":\"1300万像素相机\",\"连接方式\":\"Wi-Fi/蓝牙\",\"续航时间\":\"约2小时\",\"适用年龄\":\"8岁以上\"}", 430, 4.5, 55, "标准版,教育课程版", "https://www.ubtrobot.com/", "整机保修 1 年", inStock),
            new ProductSeed("Go2 智能电池", "宇树 Go2 原装长续航电池", "2499", "2799", "/products/battery.svg", "机器人配件", "宇树科技", "ACCESSORY", 0, 0, "MEDIUM", "适配宇树 Go2 系列，支持快速拆装，用于延长户外运行时间。", "{\"容量\":\"15000mAh\",\"电压\":\"28.8V\",\"适配型号\":\"Go2 Air/Pro/EDU\",\"配件类型\":\"动力电池\"}", 260, 4.8, 45, "长续航版", "https://www.unitree.com/go2", accessoryWarranty, inStock),
            new ProductSeed("Go2 激光雷达防护罩", "Go2 系列传感器防护配件", "399", "459", "/products/sensor.svg", "机器人配件", "宇树科技", "ACCESSORY", 0, 0, "MEDIUM", "用于日常运输和教学场景中的雷达区域防护，不影响正常视场。", "{\"材质\":\"工程塑料\",\"适配型号\":\"Go2系列\",\"配件类型\":\"结构防护\",\"安装方式\":\"螺丝固定\"}", 188, 4.7, 66, "黑色,灰色", "https://www.unitree.com/go2", accessoryWarranty, inStock),
            new ProductSeed("RoboMaster S1 装甲套件", "教育竞技扩展配件", "699", "799", "/products/armor.svg", "机器人配件", "大疆", "ACCESSORY", 0, 0, "MEDIUM", "适配 RoboMaster S1 的竞技防护和替换套件。", "{\"材质\":\"工程塑料\",\"适配型号\":\"RoboMaster S1\",\"配件类型\":\"竞技装甲\",\"颜色\":\"黑红\"}", 180, 4.6, 60, "标准套件", "https://www.dji.com/robomaster-s1", accessoryWarranty, inStock),
            new ProductSeed("Air 3S 智能飞行电池", "DJI Air 3S 原装飞行电池", "1099", "1199", "/products/battery.svg", "机器人配件", "大疆", "ACCESSORY", 0, 0, "MEDIUM", "为 Air 3S 提供最长约 45 分钟飞行时间，支持充电管家电量集成功能。", "{\"容量\":\"4276mAh\",\"适配型号\":\"DJI Air 3S\",\"配件类型\":\"飞行电池\",\"标称电压\":\"14.6V\"}", 510, 4.8, 92, "单块电池,两电池套装", "https://store.dji.com/product/dji-air-3s-intelligent-flight-battery", accessoryWarranty, inStock),
            new ProductSeed("Mavic 3 系列低噪桨", "DJI Mavic 3 原装替换桨叶", "129", "149", "/products/brush.svg", "机器人配件", "大疆", "ACCESSORY", 0, 0, "MEDIUM", "经过动平衡测试的低噪声替换桨叶，适配 Mavic 3 系列机型。", "{\"数量\":\"一对\",\"适配型号\":\"Mavic 3系列\",\"配件类型\":\"桨叶\",\"安装方式\":\"快拆\"}", 680, 4.8, 180, "一对装,两对装", "https://store.dji.com/", accessoryWarranty, inStock),
            new ProductSeed("扫地机器人通用边刷", "主流清洁机器人耗材配件", "89", "109", "/products/brush.svg", "机器人配件", "石头科技", "ACCESSORY", 0, 0, "MEDIUM", "适配平台内多款主流扫地机器人，购买前需根据适配列表确认具体型号。", "{\"材质\":\"尼龙\",\"数量\":\"2只\",\"配件类型\":\"清扫耗材\",\"建议周期\":\"3～6个月\"}", 860, 4.5, 220, "两只装,四只装", "https://www.roborock.com/", accessoryWarranty, inStock),
            new ProductSeed("全能基站集尘袋套装", "清洁机器人基站替换尘袋", "69", "89", "/products/brush.svg", "机器人配件", "石头科技", "ACCESSORY", 0, 0, "MEDIUM", "三层过滤结构的基站集尘袋，适配平台标注的石头和追觅全能基站型号。", "{\"容量\":\"2.5L\",\"数量\":\"3只\",\"配件类型\":\"集尘耗材\",\"建议周期\":\"6～8周\"}", 740, 4.6, 240, "三只装,六只装", "https://www.roborock.com/", accessoryWarranty, inStock),
            new ProductSeed("机器人开发传感器套件", "教育编程扩展套件", "399", "499", "/products/sensor.svg", "机器人配件", "大疆", "ACCESSORY", 0, 0, "MEDIUM", "包含超声波、红外和姿态传感器，可用于教育机器人和开发板实验。", "{\"传感器\":\"超声波/红外/IMU\",\"接口\":\"通用GPIO\",\"配件类型\":\"教学扩展\",\"适用场景\":\"STEM实验\"}", 410, 4.7, 90, "基础套件,课堂十套装", "https://www.dji.com/robomaster-s1", accessoryWarranty, inStock)
        ));

        catalog.addAll(List.of(
            new ProductSeed("石头 Qrevo Curv", "双机械臂全能扫拖机器人", "4599", "5299", "/products/real/roborock-g20s.jpeg", "地面清洁", "石头科技", "ROBOT", 180, 0, "HIGH", "面向养宠与复杂家具环境，具备双机械臂贴边、防缠绕清扫和全能基站维护能力。", "{\"吸力\":\"18500Pa\",\"导航方式\":\"PreciSense激光导航\",\"避障系统\":\"Reactive AI\",\"基站能力\":\"热水洗/烘干/集尘\",\"越障能力\":\"4cm\"}", 286, 4.8, 70, "标准版,配件套装版", "https://global.roborock.com/pages/roborock-qrevo-curv-series", homeWarranty, inStock),
            new ProductSeed("石头 Saros 10R", "超薄旗舰扫拖机器人", "6999", "7999", "/products/real/roborock-g20s.jpeg", "地面清洁", "石头科技", "ROBOT", 180, 0, "PRO", "7.98 厘米超薄机身配合 StarSight 2.0 导航，适合低矮家具多、障碍复杂的家庭。", "{\"吸力\":\"20000Pa\",\"机身高度\":\"7.98cm\",\"导航方式\":\"StarSight 2.0\",\"基站能力\":\"热水洗/烘干/集尘\",\"越障能力\":\"4cm\"}", 168, 4.9, 46, "标准版,上下水版", "https://global.roborock.com/pages/roborock-saros-10r", homeWarranty, inStock),
            new ProductSeed("科沃斯 DEEBOT X9 PRO OMNI", "滚筒活水洗地旗舰", "6799", "7599", "/products/real/ecovacs-x9-pro.jpg", "地面清洁", "科沃斯", "ROBOT", 225, 0, "PRO", "OZMO ROLLER 滚筒持续自清洁，配合大风量清扫和 AI 污渍识别处理重污区域。", "{\"吸力\":\"16600Pa\",\"拖地系统\":\"OZMO ROLLER\",\"避障系统\":\"AIVI 3D\",\"基站能力\":\"温控洗/烘干/集尘\",\"电池容量\":\"6400mAh\"}", 142, 4.8, 42, "黑色,白色", "https://www.ecovacs.com/us/shop/deebot-robotic-vacuum-cleaner/deebot-x9-pro-omni", homeWarranty, inStock),
            new ProductSeed("科沃斯 DEEBOT T50 PRO OMNI", "81毫米超薄全能扫拖机器人", "4299", "4999", "/products/real/ecovacs-t30-pro.jpg", "地面清洁", "科沃斯", "ROBOT", 220, 0, "HIGH", "超薄机身可进入床底和沙发底，兼顾防缠绕、贴边拖地和全自动基站维护。", "{\"吸力\":\"15000Pa\",\"机身高度\":\"81mm\",\"避障系统\":\"AIVI 3D 3.0\",\"防缠绕\":\"ZeroTangle 2.0\",\"基站能力\":\"洗烘/集尘/补水\"}", 214, 4.7, 68, "标准版,自动上下水版", "https://www.ecovacs.com/ca/shop/deebot-robotic-vacuum-cleaner/deebot-t50-pro-omni", homeWarranty, inStock),
            new ProductSeed("云鲸 Freo Z10 Ultra", "双目 AI 旗舰扫拖机器人", "5999", "6799", "/products/real/narwal-z10-ultra.png", "地面清洁", "云鲸智能", "ROBOT", 210, 0, "PRO", "双目视觉识别常见家居障碍，支持热水洗拖布、边角外扩与长周期自动集尘。", "{\"吸力\":\"18000Pa\",\"避障系统\":\"双RGB相机+三激光\",\"拖布压力\":\"8N\",\"基站能力\":\"热水洗/烘干/集尘\",\"识别物体\":\"200+\"}", 176, 4.8, 48, "灰色,白色", "https://us.narwal.com/products/narwal-freo-z10-ultra-robot-vacuum-mop", homeWarranty, inStock),
            new ProductSeed("云鲸 Freo X10 Pro", "全能基站扫拖机器人", "4299", "4999", "/products/real/narwal-z10-ultra.png", "地面清洁", "云鲸智能", "ROBOT", 200, 0, "HIGH", "面向日常全屋清洁，采用防缠绕滚刷、智能复洗复拖和全自动基站。", "{\"吸力\":\"11000Pa\",\"电池容量\":\"5000mAh\",\"导航方式\":\"激光导航\",\"毛发处理\":\"防缠绕滚刷\",\"基站能力\":\"洗拖布/烘干/集尘\"}", 152, 4.7, 61, "标准版,耗材套装版", "https://us.narwal.com/products/freo-x10-pro-robot-vacuum-mop", homeWarranty, inStock),
            new ProductSeed("追觅 X50 Ultra", "仿生越障旗舰扫拖机器人", "6499", "7299", "/products/real/dreame-x40-ultra.png", "地面清洁", "追觅科技", "ROBOT", 220, 0, "PRO", "ProLeap 仿生机械足可处理较高门槛，升降雷达兼顾超薄通行与全屋导航。", "{\"吸力\":\"20000Pa\",\"续航时间\":\"220分钟\",\"越障能力\":\"最高6cm\",\"导航方式\":\"升降DToF\",\"基站能力\":\"80℃洗拖布/烘干/集尘\"}", 238, 4.9, 50, "水箱版,自动上下水版", "https://www.dreametech.com/products/x50-ultra-robot-vacuum", homeWarranty, inStock),
            new ProductSeed("追觅 L40 Ultra Gen 2", "高吸力全能扫拖机器人", "4599", "5299", "/products/real/dreame-x40-ultra.png", "地面清洁", "追觅科技", "ROBOT", 190, 0, "HIGH", "提供高吸力、拖布外扩和基站自维护，适合希望兼顾性能与预算的家庭。", "{\"吸力\":\"25000Pa\",\"电池容量\":\"5200mAh\",\"边角清洁\":\"MopExtend\",\"避障系统\":\"3D结构光\",\"基站能力\":\"热水洗/烘干/集尘\"}", 264, 4.7, 76, "标准版,耗材套装版", "https://www.dreametech.com/products/l40-ultra-gen2-robot-vacuum", homeWarranty, inStock),
            new ProductSeed("小米 Robot Vacuum X20 Max", "全能基站扫拖机器人", "2999", "3499", "/products/real/xiaomi-s20-plus.webp", "地面清洁", "小米", "ROBOT", 120, 0, "HIGH", "支持自动集尘、拖布清洗烘干和伸缩贴边，适合米家智能家居用户。", "{\"吸力\":\"8000Pa\",\"电池容量\":\"5200mAh\",\"续航时间\":\"120分钟\",\"导航方式\":\"LDS激光导航\",\"基站能力\":\"洗烘/集尘/补水\"}", 512, 4.6, 106, "标准版,耗材套装版", "https://www.mi.com/ae-en/product/xiaomi-robot-vacuum-x20-max/", homeWarranty, inStock),
            new ProductSeed("小米 Robot Vacuum S20+", "双旋拖布扫拖机器人", "1799", "2199", "/products/real/xiaomi-s20-plus.webp", "地面清洁", "小米", "ROBOT", 170, 0, "MEDIUM", "双旋转拖布、激光导航和多档吸力，覆盖入门家庭的日常扫拖需求。", "{\"吸力\":\"6000Pa\",\"续航时间\":\"170分钟\",\"导航方式\":\"LDS激光导航\",\"拖布类型\":\"双旋转拖布\",\"控制方式\":\"米家App\"}", 688, 4.6, 142, "白色,黑色", "https://www.mi.com/global/product/xiaomi-robot-vacuum-s20-plus/", homeWarranty, inStock),

            new ProductSeed("科沃斯 WINBOT W3 OMNI", "带自清洁基站的擦窗机器人", "4999", "5599", "/products/real/ecovacs-winbot-w3.webp", "擦窗机器人", "科沃斯", "ROBOT", 110, 0, "PRO", "基站可自动清洗擦窗布，机器人支持多种路线与边缘清洁，适合大面积玻璃。", "{\"吸附力\":\"8000Pa\",\"续航时间\":\"110分钟\",\"导航系统\":\"WIN-SLAM 5.0\",\"基站能力\":\"Vortex Wash洗布\",\"安全系统\":\"12重防护\"}", 124, 4.8, 42, "标准版,清洁液套装", "https://www.ecovacs.com/us/shop/winbot-window-cleaning-robot/winbot-w3-omni", homeWarranty, inStock),
            new ProductSeed("科沃斯 WINBOT W2 PRO OMNI", "便携基站擦窗机器人", "3499", "3999", "/products/real/ecovacs-winbot-w3.webp", "擦窗机器人", "科沃斯", "ROBOT", 110, 0, "HIGH", "便携基站整合供电与安全绳收纳，适合落地窗、阳台玻璃和镜面日常维护。", "{\"导航系统\":\"WIN-SLAM 4.0\",\"喷淋系统\":\"三喷头广角喷淋\",\"清洁模式\":\"多模式\",\"安全系统\":\"多重防坠\",\"控制方式\":\"App/按键\"}", 176, 4.7, 58, "标准版,清洁布套装", "https://www.ecovacs.com/us/shop/winbot-window-cleaning-robot/winbot-w2-pro-omni", homeWarranty, inStock),
            new ProductSeed("HOBOT S7 Pro", "双震动擦窗机器人", "3999", "4499", "/products/real/hobot-s7-pro.jpg", "擦窗机器人", "HOBOT", "ROBOT", 20, 0, "HIGH", "双向超声波喷雾与高频震动擦布配合，可处理有框及无框玻璃。", "{\"擦拭频率\":\"600次/分钟\",\"喷淋方式\":\"双向超声波\",\"清洁速度\":\"约2.6分钟/㎡\",\"备用电源\":\"20分钟\",\"控制方式\":\"App/遥控器\"}", 94, 4.7, 35, "标准版,清洁布套装", "https://www.hobot.com.tw/category/product/hobot/", homeWarranty, inStock),
            new ProductSeed("HOBOT 2S", "双喷雾智能擦窗机器人", "2299", "2699", "/products/real/hobot-s7-pro.jpg", "擦窗机器人", "HOBOT", "ROBOT", 20, 0, "MEDIUM", "双边超声波喷雾与路径规划兼顾清洁效率和使用安全，适合日常窗面维护。", "{\"喷淋方式\":\"双边超声波\",\"清洁速度\":\"约2.4分钟/㎡\",\"备用电源\":\"20分钟\",\"安全绳强度\":\"230kgf\",\"控制方式\":\"App/遥控器\"}", 132, 4.6, 48, "标准版,耗材套装", "https://www.hobot.com.tw/hobot-2s/", homeWarranty, inStock),

            new ProductSeed("Mammotion LUBA 2 AWD", "全轮驱动无边界割草机器人", "25999", "28999", "/products/real/mammotion-luba2.jpg", "庭院与泳池", "Mammotion", "ROBOT", 180, 0, "PRO", "全轮驱动可应对复杂坡地，RTK 与视觉融合导航无需预埋边界线。", "{\"适用面积\":\"最高约10000㎡\",\"最大坡度\":\"80%\",\"导航方式\":\"RTK+AI视觉\",\"切割宽度\":\"约40cm\",\"分区数量\":\"最高100区\"}", 72, 4.8, 18, "3000X,5000X,10000X", "https://us.mammotion.com/products/luba-2-awd-robot-lawn-mower", homeWarranty, inStock),
            new ProductSeed("Mammotion YUKA mini", "小型庭院无边界割草机器人", "9999", "10999", "/products/real/mammotion-luba2.jpg", "庭院与泳池", "Mammotion", "ROBOT", 150, 0, "HIGH", "面向中小庭院，支持自动建图、多区域管理和视觉辅助避障。", "{\"适用面积\":\"约600～800㎡\",\"导航方式\":\"RTK+AI视觉\",\"边界线\":\"无需预埋\",\"控制方式\":\"App\",\"防水等级\":\"户外防护\"}", 98, 4.7, 24, "600H,800H", "https://us.mammotion.com/products/yuka-mini-robot-lawn-mower", homeWarranty, inStock),
            new ProductSeed("Segway Navimow i105E", "免埋线智能割草机器人", "6499", "7299", "/products/real/segway-navimow-i105e.jpg", "庭院与泳池", "Segway Navimow", "ROBOT", 60, 0, "HIGH", "EFLS 定位和视觉辅助建图降低安装门槛，适合约 500 平方米的家庭庭院。", "{\"适用面积\":\"500㎡\",\"最大坡度\":\"30%\",\"切割宽度\":\"18cm\",\"导航方式\":\"EFLS+视觉\",\"整机重量\":\"约10.9kg\"}", 126, 4.7, 31, "i105E标准版", "https://navimow.segway.com/collections/navimow-i", homeWarranty, inStock),
            new ProductSeed("Beatbot AquaSense 2 Ultra", "AI 五合一泳池清洁机器人", "18999", "21999", "/products/real/beatbot-aquasense2-ultra.jpeg", "庭院与泳池", "Beatbot", "ROBOT", 300, 0, "PRO", "AI 视觉和多传感器完成泳池建图，可覆盖水面、池底、池壁与水线。", "{\"清洁范围\":\"水面/池底/池壁/水线\",\"电池容量\":\"13400mAh\",\"池底续航\":\"最长5小时\",\"导航方式\":\"HybridSense AI\",\"清洁系统\":\"5合1\"}", 82, 4.8, 20, "标准版,澄清剂套装", "https://beatbot.com/products/aquasense-2-ultra", homeWarranty, inStock),
            new ProductSeed("Beatbot AquaSense 2 Pro", "全泳池无线清洁机器人", "13999", "15999", "/products/real/beatbot-aquasense2-ultra.jpeg", "庭院与泳池", "Beatbot", "ROBOT", 300, 0, "HIGH", "无线完成池底、池壁、水线及水面清洁，并支持 App 查看状态和一键靠边。", "{\"清洁范围\":\"水面/池底/池壁/水线\",\"电池容量\":\"13400mAh\",\"水面续航\":\"最长11小时\",\"传感器数量\":\"22个\",\"过滤篮\":\"3.7L\"}", 108, 4.7, 28, "标准版,耗材套装", "https://beatbot.com/products/aquasense-2-pro", homeWarranty, inStock),
            new ProductSeed("Aiper Scuba X1 Pro Max", "全场景无线泳池清洁机器人", "12999", "14999", "/products/real/aiper-scuba-x1-pro-max.jpeg", "庭院与泳池", "Aiper", "ROBOT", 300, 0, "PRO", "九电机系统和多传感器建图覆盖水面到池底，支持微米级过滤与 App 管理。", "{\"清洁范围\":\"水面/池底/池壁/水线\",\"水流量\":\"8500GPH\",\"传感器数量\":\"40个\",\"最小过滤粒径\":\"3μm\",\"池底续航\":\"最长5小时\"}", 76, 4.8, 19, "标准版,微滤套装", "https://aiper.com/us/aiper-scuba-series/aiper-scuba-x1-pro-max", homeWarranty, inStock),

            new ProductSeed("Sony aibo ERS-1000", "自主成长机器宠物", "22999", "24999", "/products/real/sony-aibo.jpg", "陪伴娱乐", "Sony", "ROBOT", 120, 0, "PRO", "通过摄像头、触摸传感器和云端学习形成个性，提供动作、表情与家庭互动体验。", "{\"关节自由度\":\"22\",\"续航时间\":\"约2小时\",\"感知系统\":\"双摄像头+触摸/距离传感器\",\"连接方式\":\"Wi-Fi/移动网络\",\"服务要求\":\"需云服务计划\"}", 66, 4.8, 12, "象牙白,芝麻黑", "https://electronics.sony.com/more/aibo/p/ers1000", homeWarranty, inStock),
            new ProductSeed("KEYi Loona Petbot", "可移动 AI 机器宠物", "3199", "3699", "/products/real/keyi-loona.webp", "陪伴娱乐", "KEYi Tech", "ROBOT", 90, 0, "HIGH", "支持人脸、手势和物体识别，可自主移动、表达情绪并提供远程看家能力。", "{\"显示屏\":\"2.4英寸LCD\",\"摄像头\":\"720P RGB\",\"感知系统\":\"3D ToF+四麦克风\",\"连接方式\":\"Wi-Fi/蓝牙\",\"编程方式\":\"Blockly\"}", 248, 4.7, 52, "基础版,充电底座套装", "https://keyirobot.com/products/loona", homeWarranty, inStock),
            new ProductSeed("Enabot EBO X", "家庭看护陪伴机器人", "6999", "7699", "/products/real/enabot-ebo-x.png", "陪伴娱乐", "Enabot", "ROBOT", 180, 0, "HIGH", "可在家中自主巡航，提供 4K 远程看护、双向通话、跌倒检测和家庭娱乐。", "{\"摄像头\":\"4K\",\"导航方式\":\"V-SLAM\",\"电池容量\":\"2500mAh\",\"续航时间\":\"2～3小时\",\"音响系统\":\"Harman调音\"}", 138, 4.7, 34, "标准版,配件套装版", "https://www.enabot.com/home-robot/ebo-x", homeWarranty, inStock),
            new ProductSeed("Miko 3", "儿童 AI 学习陪伴机器人", "1999", "2399", "/products/real/miko-3.jpg", "陪伴娱乐", "Miko", "ROBOT", 300, 0, "MEDIUM", "面向儿童的对话、故事、游戏与 STEM 学习机器人，家长可通过应用管理内容和使用时长。", "{\"适用年龄\":\"5～10岁\",\"显示屏\":\"触控屏\",\"摄像头\":\"广角HD\",\"连接方式\":\"Wi-Fi/蓝牙\",\"家长控制\":\"支持\"}", 316, 4.6, 82, "红色,蓝色", "https://miko.ai/products/miko-3", homeWarranty, inStock),

            new ProductSeed("大疆 Neo", "135克掌上跟拍无人机", "1299", "1499", "/products/real/dji-neo.jpg", "航拍无人机", "大疆", "ROBOT", 18, 0.135, "MEDIUM", "支持掌上起降、AI 跟拍与 4K 增稳视频，适合入门用户和日常短视频记录。", "{\"起飞重量\":\"135g\",\"最高视频\":\"4K\",\"飞行时间\":\"18分钟\",\"起降方式\":\"掌上起降\",\"智能功能\":\"AI跟拍/一键短片\"}", 980, 4.7, 160, "单机,畅飞套装", "https://www.dji.com/neo", droneWarranty, inStock),
            new ProductSeed("大疆 Flip", "全包围桨叶轻量航拍无人机", "3499", "3999", "/products/real/dji-flip.jpg", "航拍无人机", "大疆", "ROBOT", 31, 0.249, "HIGH", "低于 249 克并配备全包围桨叶保护，兼顾跟拍安全、便携性和 4K 画质。", "{\"起飞重量\":\"<249g\",\"最高视频\":\"4K/60fps HDR\",\"飞行时间\":\"31分钟\",\"安全结构\":\"全包围桨叶保护\",\"智能功能\":\"AI主体跟随\"}", 532, 4.8, 104, "RC-N3套装,RC 2畅飞套装", "https://www.dji.com/flip", droneWarranty, inStock),
            new ProductSeed("大疆 Mavic 4 Pro", "三摄旗舰航拍无人机", "14999", "16999", "/products/real/dji-mavic4-pro.jpg", "航拍无人机", "大疆", "ROBOT", 51, 1.063, "PRO", "一亿像素哈苏主摄、双长焦与 360° 旋转云台面向专业影像创作。", "{\"相机系统\":\"100MP哈苏主摄+双长焦\",\"最高视频\":\"6K/60fps HDR\",\"飞行时间\":\"51分钟\",\"图传距离\":\"30km\",\"避障系统\":\"0.1Lux夜景全向避障\"}", 184, 4.9, 36, "DJI RC 2套装,畅飞套装,512GB大师套装", "https://www.dji.com/mavic-4-pro", droneWarranty, inStock),
            new ProductSeed("Potensic ATOM 2", "轻量 4K 入门航拍无人机", "3699", "4199", "/products/real/potensic-atom2.jpg", "航拍无人机", "Potensic", "ROBOT", 32, 0.249, "HIGH", "低于 249 克，三轴云台与 AI 跟拍兼顾便携和入门创作需求。", "{\"起飞重量\":\"<249g\",\"相机系统\":\"1/2英寸Sony CMOS\",\"最高视频\":\"4K/30fps HDR\",\"飞行时间\":\"32分钟\",\"图传距离\":\"10km\"}", 212, 4.6, 58, "标准套装,畅飞套装", "https://www.potensic.com/atom-2.html", droneWarranty, inStock),

            new ProductSeed("Makeblock mBot2", "可组装 AI 编程机器人", "1299", "1499", "/products/real/makeblock-mbot2.jpg", "教育编程", "Makeblock", "ROBOT", 300, 0, "HIGH", "从图形化编程平滑进阶至 Python，支持传感器、物联网和多种结构扩展。", "{\"处理器\":\"双核240MHz\",\"编程方式\":\"图形化/Python\",\"连接方式\":\"Wi-Fi/蓝牙/USB\",\"电池容量\":\"2500mAh\",\"扩展接口\":\"10+模块\"}", 428, 4.8, 96, "单机套装,AI学习套装,课堂套装", "https://www.makeblock.com/products/buy-mbot2", "整机保修 1 年，耗材除外", inStock),
            new ProductSeed("Sphero BOLT+", "球形互动编程机器人", "1499", "1699", "/products/real/sphero-bolt-plus.png", "教育编程", "Sphero", "ROBOT", 270, 0, "HIGH", "透明球形机身内置彩色显示和多种传感器，支持绘制、积木与 JavaScript 三种编程方式。", "{\"编程方式\":\"Draw/Blocks/JavaScript\",\"显示系统\":\"彩色矩阵屏\",\"传感器\":\"陀螺仪/加速度/光线\",\"充电方式\":\"无线充电\",\"适用年级\":\"3～10年级\"}", 306, 4.8, 72, "单机,课堂套装", "https://sphero.com/products/sphero-bolt-plus", "整机保修 1 年，耗材除外", inStock),

            new ProductSeed("石头 Qrevo 清洁耗材套装", "Qrevo 系列拖布与边刷套装", "199", "239", "/products/brush.svg", "机器人配件", "石头科技", "ACCESSORY", 0, 0, "MEDIUM", "包含替换拖布、边刷与滤网，适配平台标注的 Qrevo 系列机型。", "{\"包含\":\"拖布/边刷/滤网\",\"适配型号\":\"Qrevo系列\",\"配件类型\":\"清洁耗材\",\"建议周期\":\"3～6个月\"}", 420, 4.7, 160, "基础套装,半年套装", "https://us.roborock.com/collections/accessories", accessoryWarranty, inStock),
            new ProductSeed("WINBOT 擦窗清洁布套装", "WINBOT 系列替换擦窗布", "159", "189", "/products/brush.svg", "机器人配件", "科沃斯", "ACCESSORY", 0, 0, "MEDIUM", "超细纤维擦窗布，供 WINBOT W2 与 W3 系列日常轮换使用。", "{\"材质\":\"超细纤维\",\"数量\":\"4片\",\"适配型号\":\"WINBOT W2/W3系列\",\"配件类型\":\"擦窗耗材\"}", 286, 4.7, 130, "四片装,八片装", "https://www.ecovacs.com/us/shop/winbot-window-cleaning-robot/winbot-w3-omni", accessoryWarranty, inStock),
            new ProductSeed("HOBOT 超细纤维清洁布", "HOBOT 擦窗机器人替换耗材", "169", "199", "/products/brush.svg", "机器人配件", "HOBOT", "ACCESSORY", 0, 0, "MEDIUM", "适合 HOBOT 2S 与 S7 Pro 的可清洗替换擦布，便于干湿擦轮换。", "{\"材质\":\"超细纤维\",\"数量\":\"4片\",\"适配型号\":\"HOBOT 2S/S7 Pro\",\"配件类型\":\"擦窗耗材\"}", 214, 4.6, 112, "四片装,八片装", "https://www.hobot.com.tw/category/product/hobot/", accessoryWarranty, inStock),
            new ProductSeed("Mammotion 耐久替换刀片", "LUBA 与 YUKA 系列割草刀片", "349", "399", "/products/brush.svg", "机器人配件", "Mammotion", "ACCESSORY", 0, 0, "MEDIUM", "不锈钢替换刀片适配 Mammotion 割草机器人系列，建议按使用时长翻面和更换。", "{\"材质\":\"不锈钢\",\"数量\":\"24片\",\"适配型号\":\"LUBA/YUKA系列\",\"建议周期\":\"100小时更换\"}", 168, 4.8, 88, "8片装,24片装", "https://us.mammotion.com/products/luba-endurance-replacement-blades", accessoryWarranty, inStock),
            new ProductSeed("Beatbot 双层过滤篮", "AquaSense 2 系列替换滤篮", "499", "569", "/products/brush.svg", "机器人配件", "Beatbot", "ACCESSORY", 0, 0, "MEDIUM", "原装双层过滤篮可拦截树叶、毛发和细沙，支持顶部快速取出冲洗。", "{\"容量\":\"3.5L\",\"过滤精度\":\"150μm双层\",\"适配型号\":\"AquaSense 2系列\",\"配件类型\":\"泳池过滤耗材\"}", 146, 4.8, 62, "标准滤篮", "https://eu.beatbot.com/products/beatbot-genuine-filter-basket-for-aquasense-2", accessoryWarranty, inStock),
            new ProductSeed("DJI Neo 智能飞行电池", "DJI Neo 原装轻量电池", "399", "449", "/products/battery.svg", "机器人配件", "大疆", "ACCESSORY", 0, 0, "MEDIUM", "为 DJI Neo 提供最长约 18 分钟飞行时间，适合多块轮换使用。", "{\"容量\":\"1435mAh\",\"重量\":\"约45g\",\"适配型号\":\"DJI Neo\",\"飞行时间\":\"最长18分钟\"}", 620, 4.8, 150, "单块,两块套装", "https://store.dji.com/pr/product/dji-neo-intelligent-flight-battery", accessoryWarranty, inStock),
            new ProductSeed("DJI Flip 智能飞行电池", "DJI Flip 原装飞行电池", "399", "449", "/products/battery.svg", "机器人配件", "大疆", "ACCESSORY", 0, 0, "MEDIUM", "为 DJI Flip 提供最长约 31 分钟飞行时间，支持机身与并充管家充电。", "{\"容量\":\"3110mAh\",\"重量\":\"约83.5g\",\"适配型号\":\"DJI Flip\",\"飞行时间\":\"最长31分钟\"}", 482, 4.8, 128, "单块,两块套装", "https://store.dji.com/cn/product/dji-flip-intelligent-flight-battery?vid=180981", accessoryWarranty, inStock),
            new ProductSeed("CyberPi 创新扩展套件", "mBot2 传感器与执行器扩展", "499", "599", "/products/sensor.svg", "机器人配件", "Makeblock", "ACCESSORY", 0, 0, "HIGH", "包含超声波、触摸、滑杆、舵机、电机与灯带等模块，用于 AIoT 和 Python 项目。", "{\"包含\":\"传感器/舵机/电机/灯带\",\"适配型号\":\"CyberPi/mBot2\",\"编程方式\":\"图形化/Python\",\"配件类型\":\"教育扩展\"}", 236, 4.8, 74, "创新扩展套装", "https://www.makeblock.com/products/cyberpi-innovation-add-on-pack", accessoryWarranty, inStock)
        ));

        Map<String, Product> existing = products.findAll().stream()
                .collect(Collectors.toMap(x -> x.name, Function.identity(), (a, z) -> a));
        for (ProductSeed seed : catalog) addProduct(seed, c, b, existing);
    }

    private void addProduct(ProductSeed seed, Map<String, Long> c, Map<String, Long> b,
            Map<String, Product> existing) {
        Product product = existing.get(seed.name());
        boolean isNew = product == null;
        if (!isNew) {
            // Startup creates missing demo records; it must not undo administrator edits
            // or put deliberately disabled products back on sale.
            media.migrateLegacyAssets(product, seed.image(), seed.sourceUrl());
            products.save(product);
            return;
        }
        if (isNew) product = new Product();
        product.name = seed.name();
        product.subtitle = seed.subtitle();
        product.price = new BigDecimal(seed.price());
        product.marketPrice = new BigDecimal(seed.marketPrice());
        product.image = seed.image();
        product.categoryId = c.get(seed.category());
        product.brandId = b.get(seed.brand());
        product.type = seed.type();
        product.description = seed.description();
        product.parametersJson = seed.params();
        product.enduranceMinutes = seed.endurance();
        product.payloadKg = seed.payload();
        product.intelligenceLevel = seed.level();
        product.skuOptions = seed.skuOptions();
        product.sourceUrl = seed.sourceUrl();
        product.warranty = seed.warranty();
        product.shippingNote = seed.shippingNote();
        media.applySeed(product);
        product.enabled = true;
        if (isNew) {
            product.sales = seed.sales();
            product.rating = seed.rating();
            product.stock = seed.stock();
        }
        product = products.save(product);
        existing.put(product.name, product);
    }

    private void seedCompatibility() {
        Map<String, Product> byName = products.findAll().stream()
                .collect(Collectors.toMap(x -> x.name, Function.identity(), (a, b) -> a));
        addCompatibility(byName, "宇树 Go2 Air", "Go2 智能电池", "原厂长续航电池，完全兼容");
        addCompatibility(byName, "宇树 Go2 Pro", "Go2 智能电池", "原厂长续航电池，完全兼容");
        addCompatibility(byName, "宇树 Go2 EDU", "Go2 智能电池", "原厂长续航电池，完全兼容");
        addCompatibility(byName, "宇树 Go2 Air", "Go2 激光雷达防护罩", "Go2 系列结构件，完全兼容");
        addCompatibility(byName, "宇树 Go2 Pro", "Go2 激光雷达防护罩", "Go2 系列结构件，完全兼容");
        addCompatibility(byName, "大疆 RoboMaster S1", "RoboMaster S1 装甲套件", "原厂扩展件，完全兼容");
        addCompatibility(byName, "大疆 RoboMaster S1", "机器人开发传感器套件", "教学扩展接口兼容");
        addCompatibility(byName, "大疆 Air 3S", "Air 3S 智能飞行电池", "Air 3S 原厂电池，完全兼容");
        addCompatibility(byName, "大疆 Mavic 3 Pro", "Mavic 3 系列低噪桨", "Mavic 3 系列原厂规格，完全兼容");
        for (String model : List.of("石头 G20S Ultra", "石头 P20 Pro", "石头 Qrevo S",
                "科沃斯 T30 Pro", "科沃斯 X5 PRO OMNI", "云鲸 J5", "云鲸 Freo X Ultra", "追觅 X40 Ultra")) {
            addCompatibility(byName, model, "扫地机器人通用边刷", "第三方通用耗材，下单前请按具体批次确认");
        }
        for (String model : List.of("石头 G20S Ultra", "石头 P20 Pro", "追觅 X40 Ultra")) {
            addCompatibility(byName, model, "全能基站集尘袋套装", "基站耗材，建议按适配列表确认");
        }
        for (String model : List.of("石头 Qrevo S", "石头 Qrevo Curv")) {
            addCompatibility(byName, model, "石头 Qrevo 清洁耗材套装", "Qrevo 系列专用清洁耗材");
        }
        for (String model : List.of("科沃斯 WINBOT W3 OMNI", "科沃斯 WINBOT W2 PRO OMNI")) {
            addCompatibility(byName, model, "WINBOT 擦窗清洁布套装", "WINBOT 系列替换擦窗布");
        }
        for (String model : List.of("HOBOT S7 Pro", "HOBOT 2S")) {
            addCompatibility(byName, model, "HOBOT 超细纤维清洁布", "HOBOT 擦窗机器人替换耗材");
        }
        for (String model : List.of("Mammotion LUBA 2 AWD", "Mammotion YUKA mini")) {
            addCompatibility(byName, model, "Mammotion 耐久替换刀片", "Mammotion 原装割草刀片");
        }
        for (String model : List.of("Beatbot AquaSense 2 Ultra", "Beatbot AquaSense 2 Pro")) {
            addCompatibility(byName, model, "Beatbot 双层过滤篮", "AquaSense 2 系列原装过滤篮");
        }
        addCompatibility(byName, "大疆 Neo", "DJI Neo 智能飞行电池", "DJI Neo 原装电池，完全兼容");
        addCompatibility(byName, "大疆 Flip", "DJI Flip 智能飞行电池", "DJI Flip 原装电池，完全兼容");
        addCompatibility(byName, "Makeblock mBot2", "CyberPi 创新扩展套件", "mBot2 与 CyberPi 教育扩展兼容");
    }

    private void addCompatibility(Map<String, Product> byName, String robotName,
            String accessoryName, String note) {
        Product robot = byName.get(robotName);
        Product accessory = byName.get(accessoryName);
        if (robot == null || accessory == null) return;
        if (compat.findByRobotProductIdAndAccessoryProductId(robot.id, accessory.id).isPresent()) return;
        Compatibility relation = new Compatibility();
        relation.robotProductId = robot.id;
        relation.accessoryProductId = accessory.id;
        relation.note = note;
        compat.save(relation);
    }
}
