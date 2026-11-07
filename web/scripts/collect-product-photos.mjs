import {writeFile,mkdir} from 'node:fs/promises';
const pages=[
 ['sphero-mini','https://sphero.com/products/sphero-mini'],
 ['sphero-indi','https://sphero.com/products/indi-student-kit'],
 ['makeblock-mbot-ranger','https://www.makeblock.com/products/buy-mbot-ranger'],
 ['makeblock-codey-rocky','https://www.makeblock.com/products/buy-codey-rocky'],
 ['enabot-ebo-se','https://store.enabot.com/collections/ebo-robots/products/ebo-se-familybot'],
 ['enabot-ebo-air','https://www.enabot.com/home-robot/ebo-air'],
];
await mkdir('../tmp/product-photo-sources',{recursive:true});
const direct={
 'sphero-mini':'https://sphero.com/cdn/shop/products/Mini-Blue_800x.png?v=1741829941',
 'sphero-indi':'https://sphero.com/cdn/shop/files/indiStudentKit_MAIN_alt_2e6c5082-0cf2-425d-bc01-dd6ca3635721_800x.jpg?v=1747856281',
 'makeblock-mbot-ranger':'https://cdn.shopify.com/s/files/1/0070/5901/3716/files/Step1__Unbox_Build_1e4a5f66-8688-4d7c-82b1-391da1cf9ea3.jpg?v=1732522263&width=2048',
 'makeblock-codey-rocky':'https://cdn.shopify.com/s/files/1/0070/5901/3716/files/PC_What_s_Included_1614f245-1990-483d-b153-3207b9f15fed.webp?v=1687937967',
 'enabot-ebo-se':'https://store.enabot.com/cdn/shop/files/EBO_SE-product_pic.jpg?v=1770976552',
 'enabot-ebo-air':'https://store.enabot.com/cdn/shop/files/EBOAir_productphoto1.jpg?v=1771132185',
};
for(const [name,url]of pages){
 try{
  let imageUrl=direct[name];
  if(name.startsWith('makeblock-')&&!imageUrl){
    const r=await fetch(url+'.js',{signal:AbortSignal.timeout(20000)});if(!r.ok)throw Error(`Product JSON HTTP ${r.status}`);
    const p=await r.json();imageUrl=p.featured_image||p.images?.[0];if(imageUrl)imageUrl=new URL(imageUrl,url).href;
  }
  if(!imageUrl){const response=await fetch(url,{signal:AbortSignal.timeout(20000),headers:{'User-Agent':'Mozilla/5.0'}});
  if(!response.ok)throw Error(`HTTP ${response.status}`);
  const html=await response.text();
  const tags=html.match(/<meta\b[^>]*>/gi)||[];
  const tag=tags.find(t=>/\b(?:property|name)=["']og:image["']/i.test(t));
  const raw=tag?.match(/\bcontent=["']([^"']+)["']/i)?.[1];
  if(!raw)throw Error('No product image metadata');
  imageUrl=new URL(raw.replaceAll('&amp;','&'),url).href;}
  const image=await fetch(imageUrl,{signal:AbortSignal.timeout(20000)});
  const type=image.headers.get('content-type')||'';
  if(!image.ok||!/^image\/(jpeg|png|webp)/.test(type))throw Error('Invalid image response: '+type);
  const bytes=Buffer.from(await image.arrayBuffer());if(bytes.length<1000||bytes.length>10000000)throw Error('Unexpected image size');
  const extension=type.includes('png')?'png':type.includes('webp')?'webp':'jpg';
  const file=`../tmp/product-photo-sources/${name}.${extension}`;
  await writeFile(file,bytes);
  console.log(JSON.stringify({name,url,imageUrl,file,bytes:bytes.length}));
 }catch(error){console.log(JSON.stringify({name,error:error.message}));}
}
