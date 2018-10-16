#include <pbr/pbr.sh>
#include <pbr/light.sh>

#include <gi/gi.sh>
#include <gi/light.sh>

#include <encode.sh>
#include <bgfx_compute.sh>

//IMAGE3D_RO(s_voxels_albedo, rgba8, 0);
UIMAGE3D_RO(s_voxels_albedo,  r32ui, 0);
UIMAGE3D_RO(s_voxels_normals, r32ui, 1);
UIMAGE3D_RW(s_voxels_light,   r32ui, 2);

#ifdef SHADOWS
SAMPLER2D(s_shadowmap, 3);
SAMPLERCUBE(s_shadowmap_cube, 4);
#endif


NUM_THREADS(64, 1, 1)
void main()
{
    ivec3 coord = ivec3(gl_GlobalInvocationID.xyz);
    //vec4 voxel_color = imageLoad(s_voxels_albedo, coord);
    vec4 voxel_color = decodeRGBA8(imageLoad(s_voxels_albedo, coord).x) / 255.0;
    if (voxel_color.a == 0.0) return;

    uint unor = imageLoad(s_voxels_normals, coord).r;
    vec3 voxel_normal = normalize(decodeNormal(unor));

    const vec3 hres = u_voxelgi_resolution / 2;
    vec3 voxel_position = ((gl_GlobalInvocationID.xyz - hres) / hres) * u_voxelgi_extents;
    voxel_position -= voxel_normal * 0.01; // Offset

    vec3 diffuse = compute_voxel_lights(voxel_position, voxel_color.rgb, voxel_normal);
    
    float alpha = 1.0;//(decodeRGBA8(imageLoad(s_voxels_light, coord).x) / 255.0).a;
    uint color_enc = encodeRGBA8(vec4(diffuse, alpha) * 255.0);
    
    //Light light = preread_light(0, LIGHT_DIRECT);
    //color_enc = encodeNormal(light.direction);
    //color_enc = unor;
    
#if BGFX_SHADER_LANGUAGE_HLSL
    InterlockedAdd(s_voxels_light.m_texture[coord], color_enc);
#else
    imageAtomicAdd(s_voxels_light, coord, color_enc);
#endif
}