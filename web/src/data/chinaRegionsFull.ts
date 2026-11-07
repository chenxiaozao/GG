import provinces from "@province-city-china/province";
import cities from "@province-city-china/city";
import areas from "@province-city-china/area";

export type RegionTree = Record<string, Record<string, string[]>>;
type ProvinceRecord = { code: string; name: string; province: string };
type CityRecord = ProvinceRecord & { city: string };
type AreaRecord = CityRecord & { area: string };

/** 8.5.8 固定快照，基于 GB/T 2260。大陆地区覆盖全部省、地级市及县级行政区。 */
function buildRegionTree(): RegionTree {
  const tree: RegionTree = {};
  const provinceRows = provinces as ProvinceRecord[];
  const cityRows = cities as CityRecord[];
  const areaRows = areas as AreaRecord[];
  for (const province of provinceRows) {
    tree[province.name] = {};
    const provinceCities = cityRows.filter((item) => item.province === province.province);
    if (provinceCities.length) {
      for (const city of provinceCities) {
        const displayName = city.name.replace(`${province.name}-`, "");
        tree[province.name][displayName] = areaRows
          .filter((item) => item.province === province.province && item.city === city.city)
          .map((item) => item.name);
      }
    } else {
      const directAreas = areaRows.filter((item) => item.province === province.province).map((item) => item.name);
      if (directAreas.length) tree[province.name][province.name] = directAreas;
    }
  }
  tree["台湾省"] = {
    "台北市": ["中正区", "大同区", "中山区", "松山区", "大安区", "万华区", "信义区", "士林区", "北投区", "内湖区", "南港区", "文山区"],
    "新北市": ["板桥区", "三重区", "中和区", "永和区", "新庄区", "新店区", "土城区", "芦洲区", "汐止区", "淡水区"],
    "桃园市": ["桃园区", "中坜区", "平镇区", "八德区", "杨梅区", "芦竹区", "龟山区"],
    "台中市": ["中区", "东区", "南区", "西区", "北区", "西屯区", "南屯区", "北屯区", "丰原区", "大里区"],
    "台南市": ["中西区", "东区", "南区", "北区", "安平区", "安南区", "永康区", "新营区"],
    "高雄市": ["新兴区", "前金区", "苓雅区", "盐埕区", "鼓山区", "左营区", "三民区", "凤山区"],
  };
  tree["香港特别行政区"] = { "香港特别行政区": ["中西区", "湾仔区", "东区", "南区", "油尖旺区", "深水埗区", "九龙城区", "黄大仙区", "观塘区", "葵青区", "荃湾区", "屯门区", "元朗区", "北区", "大埔区", "沙田区", "西贡区", "离岛区"] };
  tree["澳门特别行政区"] = { "澳门特别行政区": ["花地玛堂区", "圣安多尼堂区", "大堂区", "望德堂区", "风顺堂区", "嘉模堂区", "圣方济各堂区", "路凼填海区"] };
  return tree;
}

export const chinaRegions: RegionTree = buildRegionTree();
export const regionDataVersion = "@province-city-china 8.5.8 / GB/T 2260 snapshot";
