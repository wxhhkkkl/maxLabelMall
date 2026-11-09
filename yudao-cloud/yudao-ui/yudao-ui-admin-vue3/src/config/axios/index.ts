import { service } from './service'

import { config } from './config'

const { default_headers } = config

const request = (option: any) => {
  const { headersType, headers, ...otherOption } = option
  return service({
    ...otherOption,
    headers: {
      'Content-Type': headersType || default_headers,
      ...headers
    }
  })
}
export default {
  get: async <T = any>(option: any) => {
    const res = await request({ method: 'GET', ...option })
    return res.data as unknown as T
  },
  post: async <T = any>(option: any) => {
    const res = await request({ method: 'POST', ...option })
    return res.data as unknown as T
  },
  postOriginal: async (option: any) => {
    const res = await request({ method: 'POST', ...option })
    return res
  },
  delete: async <T = any>(option: any) => {
    const res = await request({ method: 'DELETE', ...option })
    return res.data as unknown as T
  },
  put: async <T = any>(option: any) => {
    const res = await request({ method: 'PUT', ...option })
    return res.data as unknown as T
  },
  download: async <T = any>(option: any) => {
    const res = await request({ method: 'GET', responseType: 'blob', ...option })
    return res as unknown as Promise<T>
  },
  upload: async <T = any>(option: any) => {
    // ⚠️ `infra/file/upload` 传的是普通对象（file + directory），必须转成 FormData；
    // 其它上传（CRM 导入、工资导入、平台内容）已经自己构造了 FormData，不能覆盖。
    if (!(option.data instanceof FormData)) {
      const formData = new FormData()
      Object.keys(option.data || {}).forEach((key) => {
        const value = option.data[key]
        if (value !== undefined && value !== null) {
          formData.append(key, value)
        }
      })
      option.data = formData
    }
    option.headersType = 'multipart/form-data'
    const res = await request({ method: 'POST', ...option })
    return res as unknown as Promise<T>
  }
}
