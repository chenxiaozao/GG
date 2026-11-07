type Spec = { key: string; label: string; value: string; explanation: string };
const definitions: Record<string, [string[],string]> = {
  '吸力': [['吸力'],'用于描述吸尘能力；不同测试条件的数字不能直接等同于清洁效果。'],
  '导航方式': [['导航方式','导航系统'],'用于定位和规划路线，不等同于避障能力。'],
  '避障系统': [['避障系统'],'识别并绕开障碍物；细线、低矮物体仍可能需要提前收拾。'],
  '基站能力': [['基站能力'],'说明哪些维护动作可以自动完成；仍需定期清洁基站。'],
  '机身高度': [['机身高度'],'与家具底部净空比较，预留通过余量。'],
  '飞行时间': [['飞行时间','续航时间'],'通常是特定测试环境下的参考值，实际使用会受风速、负载等影响。'],
  '最高视频': [['最高视频'],'分辨率与帧率描述录制规格，不单独代表最终画质。'],
  '起飞重量': [['起飞重量'],'用于判断便携性；使用限制还应核对当地规定。'],
  '编程方式': [['编程方式'],'图形化适合入门，文本编程适合进一步学习；需核对配套软件。'],
  '传感器': [['传感器','感知系统'],'说明设备能获取哪些信息，不代表每项能力都能自主完成。'],
  '摄像头': [['摄像头'],'分辨率之外，还应结合视角、夜视及网络环境判断看护体验。'],
  '交互方式': [['交互方式','控制方式'],'区分远程控制、语音互动和自主行为。'],
  '适配型号': [['适配型号'],'必须核对具体型号与代际，不能只看品牌相同。'],
  '安全绳': [['安全绳'],'擦窗设备的独立防坠保护，应按说明固定；不能用吸附能力替代安全绳。'],
  '吸附方式': [['吸附方式'],'说明设备如何附着玻璃，使用前仍要核对适用窗面和边框。'],
  '清洁范围': [['清洁范围'],'泳池池底、池壁、水线和水面是不同清洁区域，不能据“泳池清洁”推定全部覆盖。'],
  '适用面积': [['适用面积','建议草坪面积'],'应结合地形、障碍物和可用作业时间判断，不只看标称面积。'],
  '最大坡度': [['最大坡度'],'百分比与角度不是同一单位；湿滑草地等现场条件也会影响通过能力。'],
  '切割宽度': [['切割宽度'],'描述单次切割覆盖宽度，不单独代表整片草坪的作业效率。'],
  '过滤精度': [['过滤精度','最小过滤粒径'],'可过滤的颗粒尺寸与滤网结构有关；还需定期清理过滤部件。'],
  '关节自由度': [['关节自由度'],'反映可运动关节的维度，不等同于自主智能或已开放的控制能力。'],
  '最大载重': [['最大载重','最大负载','载重'],'需区分静态、行走及不同工况的载重；未知测试条件时不直接排优劣。'],
  '最高速度': [['最高速度','最大速度'],'最高速度不代表复杂路况下的安全作业速度。'],
  '运动速度': [['运动速度'],'描述运动速度；若未注明工况，不与其他型号的最高速度直接等同。'],
  '动态负载': [['动态负载'],'行走时的载重能力，不等同于静态最大载重，需结合路面和姿态条件。'],
  '运行时间': [['运行时间'],'运行测试条件未注明时，不把它等同于满载续航。'],
  '安全系统': [['安全系统'],'列出保护措施，不表示零坠落风险；仍需按说明固定防护装置。'],
  '安全绳强度': [['安全绳强度'],'承受力不代表可以省略固定与检查，需核对说明书的使用条件。'],
  '喷淋方式': [['喷淋方式','喷淋系统'],'影响清洁液喷洒方式，不能单独推断顽固污渍的清除效果。'],
  '吸附力': [['吸附力'],'用于附着窗面；不等同于吸尘吸力，不与扫地机器人吸力横向排名。'],
  '最小通行宽度': [['最小通行宽度'],'与门口、过道净宽比较，并预留转弯和人员通行空间。'],
  '货舱结构': [['货舱结构'],'核对物品尺寸、每层载重与装卸方式，不只看舱层数量。'],
  '开发接口': [['开发接口','开发支持'],'核对接口、SDK和授权版本；同型号不同版本可能不同。'],
  '配送容量': [['配送容量','托盘数量'],'需结合每层尺寸、限重与实际物品判断能否装载。'],
  '续航时间': [['续航时间'],'测试条件下的参考时长，使用模式、负载和环境可能改变实际时长。'],
  '电池容量': [['电池容量'],'容量不能直接等同于续航，还取决于耗电、工作模式和电池状态。'],
  '配件类型': [['配件类型'],'确认用途和具体适配型号，不据名称相近推定通用。'],
};
const priorities: Record<string,string[]> = {
  '地面清洁':['吸力','导航方式','避障系统','基站能力','机身高度'],
  '航拍无人机':['起飞重量','最高视频','飞行时间','避障系统'],
  '教育编程':['编程方式','传感器','控制方式'],
  '陪伴娱乐':['摄像头','交互方式','编程方式'],
  '机器人配件':['适配型号','配件类型','容量'],
  '擦窗机器人':['导航方式','喷淋方式','安全系统','安全绳','安全绳强度','吸附力'],
  '庭院与泳池':['清洁范围','适用面积','最大坡度','切割宽度','过滤精度'],
  '四足机器人':['动态负载','最大载重','最高速度','运行时间','开发接口'],
  '人形机器人':['关节自由度','最大载重','最高速度','开发接口'],
  '商用服务':['最大载重','最小通行宽度','货舱结构','配送容量','导航方式','续航时间'],
};
function normalizedValue(key:string,value:unknown) {
  if(value===true)return '支持';if(value===false)return '不支持';
  const raw=String(value).trim();if(!raw)return '未提供';
  // Normalize only explicit, single numeric values. Preserve "up to", ranges,
  // test qualifiers and missing data rather than inventing comparable precision.
  const match=raw.match(/^(\d+(?:\.\d+)?)\s*(mm|cm|kPa|Pa|分钟|小时|min|h)$/i);
  if(!match)return raw;
  const n=Number(match[1]),unit=match[2].toLowerCase();
  if(key==='吸力'&&['kpa','pa'].includes(unit))return `${Number((n*(unit==='kpa'?1000:1)).toFixed(3))}Pa`;
  if(key==='机身高度'&&['mm','cm'].includes(unit))return `${Number((n*(unit==='mm'?0.1:1)).toFixed(3))}cm`;
  if(['飞行时间','续航时间'].includes(key)&&['分钟','小时','min','h'].includes(unit))return `${Number((n*(['小时','h'].includes(unit)?60:1)).toFixed(3))}分钟`;
  return raw;
}
export function specs(product:any): Spec[] {
  let values: Record<string,unknown> = {};
  try { const p=typeof product?.parametersJson==='string'?JSON.parse(product.parametersJson):product?.parametersJson; if(p && typeof p==='object' && !Array.isArray(p)) values=p; } catch {}
  const used=new Set<string>();
  const rows:Spec[]=[];
  for(const key of [...(priorities[product?.categoryName]||[]),...Object.keys(values)]) {
    const definition=definitions[key];
    const source=(definition?.[0]||[key]).find(k=>Object.hasOwn(values,k));
    if(!source || used.has(source)) continue;
    for(const alias of definition?.[0]||[source])used.add(alias);
    const value=values[source];
    if(typeof value!=='string' && typeof value!=='number' && typeof value!=='boolean') continue;
    rows.push({key,label:key,value:normalizedValue(key,value),explanation:definition?.[1]||'以该型号公开规格为准；未提供不等于不支持。'});
  }
  return rows;
}
export function specValue(product:any,key:string) { return specs(product).find(s=>s.key===key)?.value || '未提供'; }
export function specExplanation(key:string) { return definitions[key]?.[1] || '以该型号公开规格为准；未提供不等于不支持。'; }
