/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

import { Button } from 'antd';
import { ButtonProps } from 'antd/es/button/button';
import React, { useState } from 'react';

export const LoadingBtn: React.FC<ButtonProps> = (props) => {
  const [loading, setLoading] = useState(false);

  const handleClick = async (event: React.MouseEvent<HTMLElement, MouseEvent>) => {
    if (props.onClick) {
      setLoading(true);
      await props.onClick(event);
      setLoading(false);
    }
  };

  return <Button {...props} loading={loading} onClick={(event) => handleClick(event)}></Button>;
};
